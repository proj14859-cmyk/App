package com.gdrecorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.*;

public class MainActivity extends Activity {
    Spinner resolution, fps, bitrate;
    Switch audio, experimental;
    TextView last;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32,48,32,24);
        TextView title = new TextView(this);
        title.setText("GD Recorder · 0.2"); title.setTextSize(25); layout.addView(title);
        TextView info = new TextView(this);
        info.setText("Sin marcas de agua. Abre el juego y ponlo en horizontal ANTES de iniciar. Inicia desde aquí y vuelve al juego. Detén desde la notificación. Los videos se guardan en Movies/GDRecorder.\n\n120 fps es experimental: depende del codificador y de los fotogramas que entregue Android. Si el juego bloquea su audio, se grabará silencio.\n"); layout.addView(info);
        resolution = selector(layout,"Resolución", new String[]{"1080p (1920 × 1080)", "720p (1280 × 720)"});
        fps = selector(layout,"Fotogramas por segundo",new String[]{"60", "120", "30"});
        bitrate = selector(layout,"Video: megabits por segundo",new String[]{"24", "32", "48", "64", "16", "8"});
        audio = new Switch(this); audio.setText("Audio interno (sin micrófono)"); audio.setChecked(true); layout.addView(audio);
        experimental=new Switch(this); experimental.setText("Intentar FPS no declarados (experimental)"); layout.addView(experimental);
        TextView warning=new TextView(this); warning.setText("Este modo intenta configurar los FPS aunque Android no los declare. Puede fallar o generar menos FPS. 1080p ahora es 1920 × 1080; puede haber franjas para conservar la proporción."); layout.addView(warning);
        last=new TextView(this); layout.addView(last);
        Button start = new Button(this); start.setText("Iniciar grabación"); layout.addView(start);
        start.setOnClickListener(v -> {
            if (audio.isChecked() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10); return;
            }
            startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),20);
        });
        Button stop = new Button(this); stop.setText("Detener"); layout.addView(stop);
        stop.setOnClickListener(v -> startService(new Intent(this,RecorderService.class).setAction("STOP")));
        ScrollView scroll=new ScrollView(this); scroll.addView(layout); setContentView(scroll);
    }
    @Override protected void onResume(){
        super.onResume();
        if(last!=null)last.setText(getSharedPreferences("recording",MODE_PRIVATE).getString("last","Todavía no hay resultados de grabación."));
    }
    Spinner selector(LinearLayout layout, String label, String[] values) {
        TextView text = new TextView(this); text.setText(label); layout.addView(text);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values)); layout.addView(spinner); return spinner;
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=20 || result!=RESULT_OK || data==null) return;
        Intent intent=new Intent(this,RecorderService.class);
        intent.putExtra("grant",data).putExtra("result",result)
            .putExtra("height",resolution.getSelectedItemPosition()==0?1080:720)
            .putExtra("fps",Integer.parseInt(fps.getSelectedItem().toString()))
            .putExtra("bitrate",Integer.parseInt(bitrate.getSelectedItem().toString())*1000000)
            .putExtra("audio",audio.isChecked()).putExtra("experimental",experimental.isChecked());
        startForegroundService(intent);
    }
}
