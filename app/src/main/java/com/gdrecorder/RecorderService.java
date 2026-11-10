package com.gdrecorder;

import android.app.*;
import android.content.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.Surface;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecorderService extends Service {
    final AtomicBoolean running=new AtomicBoolean(false);
    Thread worker;
    MediaProjection projection;
    MediaProjection.Callback callback;
    VirtualDisplay display;
    MediaCodec video, audio;
    AudioRecord capture;
    Surface surface;
    MediaMuxer muxer;
    android.os.ParcelFileDescriptor descriptor;
    Uri output;
    int videoTrack=-1,audioTrack=-1;
    boolean muxStarted=false, wantAudio;
    static class Sample {
        ByteBuffer bytes;
        MediaCodec.BufferInfo info;
        int track;
    }
    final java.util.ArrayList<Sample> pending=new java.util.ArrayList<>();
    int pendingBytes;
    Handler main=new Handler(Looper.getMainLooper());
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("STOP".equals(intent.getAction())){running.set(false);if(worker==null)stopSelf();return START_NOT_STICKY;}
        if(worker!=null)return START_NOT_STICKY;
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("record","Grabación",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,RecorderService.class).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"record").setContentTitle("GD Recorder grabando")
            .setContentText("Toca Detener para guardar el video").setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"Detener",stop).build()).build());
        running.set(true);
        worker=new Thread(()->record(intent),"recorder");worker.start();
        return START_NOT_STICKY;
    }
    void record(Intent intent){
        boolean success=false;
        String message="Grabación guardada en Movies/GDRecorder";
        try{
            int shortSide=intent.getIntExtra("height",1080), rate=intent.getIntExtra("fps",60);
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
            getSystemService(android.hardware.display.DisplayManager.class).getDisplay(android.view.Display.DEFAULT_DISPLAY).getRealMetrics(metrics);
            int width,height;
            if(metrics.widthPixels>=metrics.heightPixels){height=shortSide;width=(int)Math.round((double)shortSide*metrics.widthPixels/metrics.heightPixels/2)*2;}
            else {width=shortSide;height=(int)Math.round((double)shortSide*metrics.heightPixels/metrics.widthPixels/2)*2;}
            MediaCodecInfo chosen=null;
            for(MediaCodecInfo codec:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
                if(!codec.isEncoder() || !codec.isHardwareAccelerated())continue;
                try{
                    MediaCodecInfo.CodecCapabilities caps=codec.getCapabilitiesForType("video/avc");
                    boolean supportsSurface=false;
                    for(int color:caps.colorFormats)if(color==MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)supportsSurface=true;
                    if(supportsSurface && caps.getVideoCapabilities().areSizeAndRateSupported(width,height,rate)){chosen=codec;break;}
                }catch(IllegalArgumentException ignored){}
            }
            if(chosen==null)throw new Exception("El codificador no admite esta resolución y FPS. Prueba 720p o 60 fps.");
            video=MediaCodec.createByCodecName(chosen.getName());
            MediaFormat vf=MediaFormat.createVideoFormat("video/avc",width,height);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            android.util.Range<Integer> range=chosen.getCapabilitiesForType("video/avc").getVideoCapabilities().getBitrateRange();
            vf.setInteger(MediaFormat.KEY_BIT_RATE,range.clamp(intent.getIntExtra("bitrate",16000000)));
            vf.setInteger(MediaFormat.KEY_FRAME_RATE,rate);vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,2);
            video.configure(vf,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);surface=video.createInputSurface();video.start();
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("result",-1),intent.getParcelableExtra("grant"));
            callback=new MediaProjection.Callback(){@Override public void onStop(){running.set(false);}};
            projection.registerCallback(callback,main);
            wantAudio=intent.getBooleanExtra("audio",true);
            if(wantAudio){
                if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Concede el permiso de audio desde la app.");
                AudioPlaybackCaptureConfiguration config=new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build();
                int min=AudioRecord.getMinBufferSize(48000,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT);
                capture=new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder().setSampleRate(48000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(min,16384)).setAudioPlaybackCaptureConfig(config).build();
                if(capture.getState()!=AudioRecord.STATE_INITIALIZED)throw new Exception("No se pudo iniciar el audio interno.");
                audio=MediaCodec.createEncoderByType("audio/mp4a-latm");
                MediaFormat af=MediaFormat.createAudioFormat("audio/mp4a-latm",48000,2);
                af.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);af.setInteger(MediaFormat.KEY_BIT_RATE,192000);
                audio.configure(af,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);audio.start();
            }
            ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"GD_"+System.currentTimeMillis()+".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/GDRecorder");values.put(MediaStore.Video.Media.IS_PENDING,1);
            output=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
            if(output==null)throw new Exception("No se pudo crear el archivo.");
            descriptor=getContentResolver().openFileDescriptor(output,"w");muxer=new MediaMuxer(descriptor.getFileDescriptor(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            long origin=System.nanoTime()/1000, audioFrames=0;
            if(wantAudio)capture.startRecording();
            display=projection.createVirtualDisplay("GDRecorder",width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,surface,null,null);
            long audioOrigin=System.nanoTime()/1000-origin;
            while(running.get()){
                if(wantAudio){
                    int index=audio.dequeueInputBuffer(0);
                    if(index>=0){
                        ByteBuffer input=audio.getInputBuffer(index);input.clear();
                        int bytes=capture.read(input,Math.min(input.remaining(),8192),AudioRecord.READ_NON_BLOCKING);
                        if(bytes<0)throw new Exception("Error capturando audio: "+bytes);
                        audio.queueInputBuffer(index,0,bytes,audioOrigin+audioFrames*1000000/48000,0);audioFrames+=bytes/4;
                    }
                    drain(audio,false,origin);
                }
                drain(video,true,origin);
                Thread.sleep(2);
            }
            video.signalEndOfInputStream();
            if(wantAudio){
                capture.stop();int index=audio.dequeueInputBuffer(100000);
                if(index>=0)audio.queueInputBuffer(index,0,0,audioOrigin+audioFrames*1000000/48000,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            long deadline=System.currentTimeMillis()+3000;
            boolean videoDone=false,audioDone=!wantAudio;
            while(System.currentTimeMillis()<deadline && !(videoDone&&audioDone)){
                if(!videoDone)videoDone=drain(video,true,origin);
                if(!audioDone)audioDone=drain(audio,false,origin);
                Thread.sleep(2);
            }
            if(!muxStarted)throw new Exception("La grabación fue demasiado corta.");
            muxer.stop();success=true;
        }catch(Exception e){message="No se guardó: "+e.getMessage();}
        finally{
            running.set(false);
            if(display!=null)display.release();
            if(projection!=null){projection.unregisterCallback(callback);projection.stop();}
            if(capture!=null){try{capture.stop();}catch(Exception ignored){}capture.release();}
            for(MediaCodec codec:new MediaCodec[]{video,audio})if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
            if(surface!=null)surface.release();
            if(muxer!=null)try{muxer.release();}catch(Exception ignored){}
            if(descriptor!=null)try{descriptor.close();}catch(Exception ignored){}
            if(output!=null){
                if(success){ContentValues done=new ContentValues();done.put(MediaStore.Video.Media.IS_PENDING,0);getContentResolver().update(output,done,null,null);}
                else getContentResolver().delete(output,null,null);
            }
            final String notice=message;main.post(()->android.widget.Toast.makeText(this,notice,android.widget.Toast.LENGTH_LONG).show());
            stopForeground(true);stopSelf();
        }
    }
    boolean drain(MediaCodec codec,boolean isVideo,long origin){
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        while(true){
            int index=codec.dequeueOutputBuffer(info,0);
            if(index==MediaCodec.INFO_TRY_AGAIN_LATER)return false;
            if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                int track=muxer.addTrack(codec.getOutputFormat());if(isVideo)videoTrack=track;else audioTrack=track;
                if(videoTrack>=0 && (!wantAudio || audioTrack>=0)){muxer.start();muxStarted=true;
                    for(Sample sample:pending)muxer.writeSampleData(sample.track,sample.bytes,sample.info);
                    pending.clear();pendingBytes=0;}
            }else if(index>=0){
                try{
                    if(info.size>0 && (info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        ByteBuffer buffer=codec.getOutputBuffer(index);buffer.position(info.offset);buffer.limit(info.offset+info.size);
                        if(isVideo)info.presentationTimeUs=Math.max(0,info.presentationTimeUs-origin);
                        if(muxStarted)muxer.writeSampleData(isVideo?videoTrack:audioTrack,buffer,info);
                        else {
                            if(pendingBytes+info.size>16000000)throw new IllegalStateException("El audio o video no inició a tiempo.");
                            Sample sample=new Sample();sample.track=isVideo?videoTrack:audioTrack;
                            sample.bytes=ByteBuffer.allocateDirect(info.size);sample.bytes.put(buffer);sample.bytes.flip();
                            sample.info=new MediaCodec.BufferInfo();sample.info.set(0,info.size,info.presentationTimeUs,info.flags);
                            pending.add(sample);pendingBytes+=info.size;
                        }
                    }
                }finally{codec.releaseOutputBuffer(index,false);}
                if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)return true;
            }
        }
    }
    @Override public void onDestroy(){running.set(false);super.onDestroy();}
}
