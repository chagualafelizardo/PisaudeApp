package com.example.pisaudeapp;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;

import com.example.pisaudeapp.R;

public class AboutDialog {

    private final Context context;
    private Dialog dialog;

    public AboutDialog(Context context) {
        this.context = context;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = View.inflate(context, R.layout.dialog_about, null);

        // Configurar os textos
        setupViews(dialogView);

        builder.setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        dialog = builder.create();

        // Personalizar o diálogo
        customizeDialog(dialogView);

        dialog.show();
    }

    private void setupViews(View view) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);

            // Configurar versão
            TextView tvVersion = view.findViewById(R.id.tv_app_version);
            tvVersion.setText(String.format("Versão %s", pInfo.versionName));

            // Configurar SDK
            TextView tvSdkVersion = view.findViewById(R.id.tv_sdk_version);
            String sdkInfo = String.format("Android %s (API %d)",
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT);
            tvSdkVersion.setText(sdkInfo);

            // Configurar copyright
            TextView tvCopyright = view.findViewById(R.id.tv_copyright);
            int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            tvCopyright.setText(String.format("© %d PISaude. Todos os direitos reservados.", currentYear));

            // Configurar descrição
            TextView tvDescription = view.findViewById(R.id.tv_app_description);
            String description = String.format("PISaude App v%s\n" +
                            "Serviço automático de SMS\n" +
                            "Desenvolvido para monitoramento de pacientes",
                    pInfo.versionName);
            tvDescription.setText(description);

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void customizeDialog(View view) {
        // Configurar cliques nos emails
        TextView tvDeveloperEmail = view.findViewById(R.id.tv_developer_email);
        tvDeveloperEmail.setOnClickListener(v -> sendEmail("seu.email@exemplo.com", "Contato - PISaude App"));

        TextView tvSupportEmail = view.findViewById(R.id.tv_support_email);
        tvSupportEmail.setOnClickListener(v -> sendEmail("suporte@pisaude.com", "Suporte - PISaude App"));

        // Configurar clique no telefone
        TextView tvSupportPhone = view.findViewById(R.id.tv_support_phone);
        tvSupportPhone.setOnClickListener(v -> dialPhone("(11) 99999-9999"));
    }

    private void sendEmail(String email, String subject) {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:" + email));
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        if (emailIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(Intent.createChooser(emailIntent, "Enviar email"));
        }
    }

    private void dialPhone(String phoneNumber) {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL);
        dialIntent.setData(Uri.parse("tel:" + phoneNumber.replaceAll("[^\\d+]", "")));

        if (dialIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(dialIntent);
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}