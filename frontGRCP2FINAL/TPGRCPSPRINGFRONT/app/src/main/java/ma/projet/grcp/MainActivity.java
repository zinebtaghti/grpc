package ma.projet.grcp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.ArrayAdapter;

import ma.projet.grpc.stubs.TypeCompte;


import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import androidx.appcompat.app.AlertDialog;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import ma.projet.grpc.stubs.Compte;
import ma.projet.grpc.stubs.CompteServiceGrpc;
import ma.projet.grpc.stubs.GetAllComptesRequest;
import ma.projet.grpc.stubs.GetAllComptesResponse;
import ma.projet.grpc.stubs.SaveCompteRequest;
import ma.projet.grpc.stubs.SaveCompteResponse;
import ma.projet.grpc.stubs.CompteRequest;




public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private RecyclerView recyclerView;
    private CompteAdapter compteAdapter; // Renommé pour plus de clarté
    private List<Compte> compteList = new ArrayList<>();
    private FloatingActionButton fabAddCompte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.recyclerViewComptes);
        fabAddCompte = findViewById(R.id.fabAddCompte);

        compteAdapter = new CompteAdapter(compteList); // Utilisation du nouveau nom
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(compteAdapter);

        // Charger les comptes depuis le serveur
        new Thread(() -> communicateWithServer()).start();

        // Ajouter un nouveau compte
        fabAddCompte.setOnClickListener(v -> addNewCompte());
    }

    private void communicateWithServer() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("10.0.2.2", 9090)
                .usePlaintext()
                .build();

        try {
            CompteServiceGrpc.CompteServiceBlockingStub stub = CompteServiceGrpc.newBlockingStub(channel);
            GetAllComptesRequest request = GetAllComptesRequest.newBuilder().build();
            GetAllComptesResponse response = stub.allComptes(request);

            runOnUiThread(() -> {
                compteList.clear();
                compteList.addAll(response.getComptesList());
                compteAdapter.notifyDataSetChanged();
            });
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la communication avec le serveur", e);
        } finally {
            channel.shutdown();
        }
    }

    private void addNewCompte() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_compte, null);

        EditText editTextSolde = dialogView.findViewById(R.id.editTextSolde);
        Spinner spinnerTypeCompte = dialogView.findViewById(R.id.spinnerTypeCompte);
        Button btnSaveCompte = dialogView.findViewById(R.id.btnSaveCompte);

        // Renommé en spinnerAdapter pour éviter la confusion
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"COURANT", "EPARGNE"});
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTypeCompte.setAdapter(spinnerAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Ajouter un compte")
                .setView(dialogView)
                .create();

        btnSaveCompte.setOnClickListener(v -> {
            String soldeStr = editTextSolde.getText().toString().trim();
            String typeCompte = spinnerTypeCompte.getSelectedItem().toString();

            if (!soldeStr.isEmpty()) {
                double solde = Double.parseDouble(soldeStr);

                new Thread(() -> {
                    ManagedChannel channel = ManagedChannelBuilder
                            .forAddress("10.0.2.2", 9090)
                            .usePlaintext()
                            .build();

                    try {
                        CompteServiceGrpc.CompteServiceBlockingStub stub = CompteServiceGrpc.newBlockingStub(channel);

                        SaveCompteRequest request = SaveCompteRequest.newBuilder()
                                .setCompte(
                                        CompteRequest.newBuilder()
                                                .setSolde((float) solde)
                                                .setDateCreation(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                                                .setType(TypeCompte.valueOf(typeCompte))
                                                .build()
                                )
                                .build();

                        SaveCompteResponse response = stub.saveCompte(request);

                        // Après avoir sauvegardé le compte, récupérer la liste mise à jour
                        GetAllComptesRequest getAllRequest = GetAllComptesRequest.newBuilder().build();
                        GetAllComptesResponse getAllResponse = stub.allComptes(getAllRequest);

                        runOnUiThread(() -> {
                            compteList.clear();
                            compteList.addAll(getAllResponse.getComptesList());
                            compteAdapter.notifyDataSetChanged();
                            dialog.dismiss();
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Erreur lors de l'ajout du compte", e);
                        runOnUiThread(() -> {
                            // Afficher un message d'erreur à l'utilisateur si nécessaire
                        });
                    } finally {
                        channel.shutdown();
                    }
                }).start();
            }
        });

        dialog.show();
    }
}
