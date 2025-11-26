package com.example.pisaudeapp.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class HomeViewModel extends ViewModel {

    private MutableLiveData<Integer> patientCount = new MutableLiveData<>(0);
    private MutableLiveData<Integer> smsSentToday = new MutableLiveData<>(0);
    private MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private MutableLiveData<String> error = new MutableLiveData<>();

    public LiveData<Integer> getPatientCount() {
        return patientCount;
    }

    public LiveData<Integer> getSmsSentToday() {
        return smsSentToday;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public void loadDashboardData() {
        loading.setValue(true);

        // Simular carregamento de dados da API
        // Na implementação real, você faria chamadas à sua API

        new android.os.Handler().postDelayed(() -> {
            patientCount.setValue(156); // Exemplo
            smsSentToday.setValue(23);  // Exemplo
            loading.setValue(false);
        }, 1000);
    }
}