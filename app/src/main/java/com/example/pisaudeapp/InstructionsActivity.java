package com.example.pisaudeapp;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class InstructionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instructions);

        TextView tvInstructions = findViewById(R.id.tvInstructions);

        String instructions = "📱 COMO AGENDAR CONSULTA VIA SMS:\n\n" +
                "1️⃣ Envie uma mensagem SMS para:\n" +
                "   📞 +258 85 165 5626\n\n" +
                "2️⃣ Se você tem NID (Número de Identificação):\n" +
                "   📝 consulta NID DD/MM/AAAA HH:MM\n" +
                "   ✅ Exemplo: consulta 0110000001/2015/00396 25/12/2024 14:30\n\n" +
                "3️⃣ Se não tem NID:\n" +
                "   📝 consulta DD/MM/AAAA HH:MM\n" +
                "   ✅ Exemplo: consulta 25/12/2024 14:30\n\n" +
                "4️⃣ Formatos aceites:\n" +
                "   • NID: 10 dígitos/4 dígitos/5 dígitos\n" +
                "   • Data: DD/MM/AAAA ou DD-MM-AAAA\n" +
                "   • Hora: HH:MM ou HH.MM (24h)\n\n" +
                "5️⃣ Você receberá:\n" +
                "   📨 Confirmação do agendamento\n" +
                "   📅 Data e hora da consulta\n" +
                "   👨‍⚕️ Nome do médico\n" +
                "   🔢 ID do agendamento\n\n" +
                "⚠️ IMPORTANTE:\n" +
                "   • Chegue 15 minutos antes\n" +
                "   • Traga documento de identificação\n" +
                "   • Em caso de cancelamento, contacte-nos";

        tvInstructions.setText(instructions);
    }
}