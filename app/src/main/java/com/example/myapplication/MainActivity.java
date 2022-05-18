package com.example.myapplication;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContracts;

import android.app.Activity;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import com.google.common.collect.Lists;
import com.google.protobuf.Value;


import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnInitListener{

    //https://stackoverflow.com/questions/62671106/onactivityresult-method-is-deprecated-what-is-the-alternative

    private ActivityResultLauncher<Intent> sttLauncher;
    private Intent sttIntent;
    private EditText tvStt;
    private Button btConfirmar;

    private SessionsClient sessionClient;
    private SessionName sessionName;

    private TextView dialogText;

    private final String uuid = UUID.randomUUID().toString();

    private boolean ttsReady = false;
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();
    }

    @Override
    public void onInit(int i) {
        if( i == TextToSpeech.SUCCESS){
            ttsReady = true;
            tts.setLanguage(new Locale("spa", "ES"));
        }else {
            System.out.print("Error");
        }

    }



    private void setupBot() {
        try {
            InputStream stream = this.getResources().openRawResource(R.raw.cliente);
            GoogleCredentials credentials = GoogleCredentials.fromStream(stream)
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
            String projectId = ((ServiceAccountCredentials) credentials).getProjectId();
            SessionsSettings.Builder settingsBuilder = SessionsSettings.newBuilder();
            SessionsSettings sessionsSettings = settingsBuilder.setCredentialsProvider(
                    FixedCredentialsProvider.create(credentials)).build();
            sessionClient = SessionsClient.create(sessionsSettings);
            sessionName = SessionName.of(projectId, uuid);
        } catch (Exception e) {
            showMessage("\nexception in setupBot: " + e.getMessage() + "\n");
        }
    }


    private void sendMessageToBot(String message) {
        QueryInput input = QueryInput.newBuilder().setText(
                TextInput.newBuilder().setText(message).setLanguageCode("es-ES")).build();
        Thread thread = new Thread() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void run() {
                try {
                    DetectIntentRequest detectIntentRequest =
                            DetectIntentRequest.newBuilder()
                                    .setSession(sessionName.toString())
                                    .setQueryInput(input)
                                    .build();
                    DetectIntentResponse detectIntentResponse = sessionClient.detectIntent(detectIntentRequest);
                    //intent, action, sentiment
                    if(detectIntentResponse != null) {
                        String action = detectIntentResponse.getQueryResult().getAction();
                        String intent = detectIntentResponse.getQueryResult().getIntent().getDisplayName();
                        String sentiment = detectIntentResponse.getQueryResult().getSentimentAnalysisResult().toString();
                        String botReply = detectIntentResponse.getQueryResult().getFulfillmentText();
                        if(!botReply.isEmpty()) {
                            showMessage(botReply + "\n");
                            voiceDialogFlow(botReply);
                            if(intent.equals("Cita")){
                                Map<String, Value> params = detectIntentResponse.getQueryResult().getParameters().getFieldsMap();
                                Value nombreResponse = params.get("name");
                                String nombre = String.valueOf(nombreResponse.getStringValue());
                                Value diaResponse = params.get("date");
                                String dia = String.valueOf(diaResponse.getStringValue());
                                Value horaResponse = params.get("time");
                                String hora = String.valueOf(horaResponse.getStringValue());
                                if(!hora.equals("") && !nombre.equals("") &&!dia.equals("")){
                                    System.out.println("AQUIIII CITAS" + params);
                                    saveInCalendar(dia,nombre,hora);
                                }
                            }if(intent.equals("Agenda")) {
                                Map<String, Value> params = detectIntentResponse.getQueryResult().getParameters().getFieldsMap();
                                Value contactResponse = params.get("person");
                                String contacto = String.valueOf(contactResponse.getStringValue());
                                if(!contacto.equals("")){
                                    System.out.println("AQUIIII ENCUENTRA EL CONTACTO"+params);
                                    String phoneNumber = search(contacto);
                                    if(!phoneNumber.equals("")){
                                        System.out.println("AQUIIII AGENDA" + params);
                                        callPhoneNumber(phoneNumber);
                                    }
                                }else{
                                    System.out.println("AQUIIII FALLA TODO EN AGENDA"+params);
                                }
                            }else{
                                System.out.println("AQUIIII NO DEBERÍA"+intent);
                            }
                        } else {
                            showMessage("something went wrong\n");
                        }
                    } else {
                        showMessage("connection failed\n");
                    }
                } catch (Exception e) {
                    showMessage("\nexception in thread: " + e.getMessage() + "\n");
                    e.printStackTrace();
                }
            }
        };
        thread.start();
    }

    private void showMessage(String message) {
        runOnUiThread(() -> {
            dialogText.setText(message);
            tvStt.setText("");
        });
    }




    private void onClickBtConfirmar() {
        String frase = tvStt.getText().toString();
        /*tvStt.setText("");
        tvResultado.setText("Frase leida: "+frase);*/
        if(ttsReady && frase.trim().length() > 0){
            tts.speak(frase, TextToSpeech.QUEUE_ADD, null, null);
        }else {
            showResult("No");
        }
    }

    private void voiceDialogFlow(String message) {
        String frase = message;
        if(ttsReady && frase.trim().length() > 0){
            tts.speak(frase, TextToSpeech.QUEUE_ADD, null, null);
        }else {
            showResult("No");
        }
    }



    private Intent getSttIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, new Locale("spa", "ES"));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hola");
        return intent;
    }

    private ActivityResultLauncher<Intent> getSttLauncher() {
        return registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    String text = "";
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        List<String> r = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        text = r.get(0);
                    } else if(result.getResultCode() == Activity.RESULT_CANCELED) {
                        text = "getString(R.string.error)";
                    }
                    showResult(text);
                }
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void saveInCalendar(String dia,String nombre, String hora) {

        String fecha = dia.substring(0,10);
        String time = hora.substring(10);
        String finalDate = fecha+time;
        String location = "Granada";

        DateTimeFormatter isoDateFormatter = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime ldate = LocalDateTime.parse(finalDate, isoDateFormatter);
        Date rDate = Date.from(ldate.atZone(ZoneId.of("UTC+2")).toInstant());
        long begin = rDate.getTime();

        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, nombre)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);


        //ME FALLA ESTE MALDITO IF(Ya no)!
        if (intent.resolveActivity(getPackageManager()) != null) {
            System.out.println("AQUIIII ES LA AGENDA");
            startActivity(intent);
        }
    }


    //FUNCIONES DE LLAMADA POR TELÉFONO
    public void callPhoneNumber(String phoneNumber) {
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + phoneNumber));
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    public String search(String contactName) {
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String proyeccion[] = new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME};
        String condicion = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " like ?";
        String argumentos[] = new String[]{ contactName + "%" };
        String orden = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME;
        Cursor cursor = getContentResolver().query(uri, proyeccion, condicion, argumentos, orden);
        int columnaNombre = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
        int columnaNumero = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
        String nombre, numero = "";
        if(cursor.moveToNext()) {
            nombre = cursor.getString(columnaNombre);
            numero = cursor.getString(columnaNumero);
        }
        return numero;
    }


    private void initialize() {
        Button btStt = findViewById(R.id.btStt);
        Button playButton = findViewById(R.id.btConfirmar);
        Button btDialogFlow = findViewById(R.id.btDialogFlow);
        tvStt = findViewById(R.id.tvStt);
        dialogText = findViewById(R.id.dialogResponse);

        tts = new TextToSpeech(this, this);

        sttLauncher = getSttLauncher();
        sttIntent = getSttIntent();

        btStt.setOnClickListener(view -> {
            sttLauncher.launch(sttIntent);
        });

        playButton.setOnClickListener(view -> {
            onClickBtConfirmar();
        });


        btDialogFlow.setOnClickListener(view -> {
            if(!tvStt.getText().toString().isEmpty()) {
                sendMessageToBot(tvStt.getText().toString());
            }
        });

        setupBot();



    }

    private void showResult(String result) {
        tvStt.setText(result);
    }





}/*Fin coding*/