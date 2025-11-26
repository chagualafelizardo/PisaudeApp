package com.example.pisaudeapp.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.pisaudeapp.R;
import com.example.pisaudeapp.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        setupUI();
        setupObservers();

        // Carregar dados iniciais
        homeViewModel.loadDashboardData();

        return root;
    }

    private void setupUI() {
        // ✅ USAR VIEW BINDING DIRETAMENTE - MAIS SEGURO
        binding.btnSendSms.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.nav_sms);
        });

        binding.btnViewPatients.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.nav_patients);
        });

        binding.btnTemplates.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment_content_main);
            navController.navigate(R.id.nav_templates);
        });
    }

    private void setupObservers() {
        homeViewModel.getPatientCount().observe(getViewLifecycleOwner(), count -> {
            binding.tvPatientCount.setText(String.valueOf(count));
        });

        homeViewModel.getSmsSentToday().observe(getViewLifecycleOwner(), count -> {
            binding.tvSmsToday.setText(String.valueOf(count));
        });

        homeViewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            // Mostrar/ocultar loading
            if (loading) {
                // Mostrar progress bar - se existir no layout
                if (binding.loadingProgress != null) {
                    binding.loadingProgress.setVisibility(View.VISIBLE);
                }
            } else {
                // Ocultar progress bar - se existir no layout
                if (binding.loadingProgress != null) {
                    binding.loadingProgress.setVisibility(View.GONE);
                }
            }
        });

        homeViewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                // Mostrar erro (usando Snackbar por exemplo)
                // Snackbar.make(binding.getRoot(), error, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}