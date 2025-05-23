package com.mobiledev.recipeit;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.ToggleButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.Gson;
import com.mobiledev.recipeit.Adapters.ChatHistoryAdapter;
import com.mobiledev.recipeit.Helpers.DialogHelper;
import com.mobiledev.recipeit.Helpers.RecipeApiClient;
import com.mobiledev.recipeit.Helpers.UserSessionManager;
import com.mobiledev.recipeit.Models.RecipeByChatRequest;
import com.mobiledev.recipeit.Helpers.RecipeHelper;
import com.mobiledev.recipeit.Models.ChatHistory;
import com.mobiledev.recipeit.Models.RecipeByChatRequest;
import com.mobiledev.recipeit.Models.RecipeByImageRequest;
import com.mobiledev.recipeit.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String API_ENDPOINT = "http://10.0.2.2:4000/api";

    private ActivityMainBinding binding;
    private LinearLayout chatLayout;
    private EditText inputEditText;
    private ImageView sendIcon;
    private UserSessionManager sessionManager;
    private final List<ChatHistory> chatHistories = new ArrayList<>(
            List.of(
                    ChatHistory.Server("Hello! I am your recipe assistant. How can I help you today?"),
                    ChatHistory.Server("Please upload an image of the ingredients or type your request.")
            )
    );

    private ChatHistoryAdapter chatHistoryAdapter;
    private RecyclerView chatHistoryView;
    private EditText inputEditText;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    private ToggleButton veganToggle, glutenFreeToggle, dairyFreeToggle;
    private SeekBar calorieSeekBar, recipeCountSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UserSessionManager
        sessionManager = new UserSessionManager(this);

        // Initialize UI elements using ViewBinding
        chatLayout = binding.chatLayout;
        inputEditText = binding.inputEditText;
        sendIcon = binding.sendIcon;

        // Setup click listeners
        sendIcon.setOnClickListener(v -> sendMessage());

        // Check if user is logged in
        if (!sessionManager.isLoggedIn()) {
            // Redirect to login if not logged in
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        // Set welcome message with user's name
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Welcome, " + currentUser.getDisplayName());
            }
        }

        chatHistoryView = findViewById(R.id.chatHistoryView);
        inputEditText = findViewById(R.id.inputEditText);

        chatHistoryAdapter = new ChatHistoryAdapter(this, chatHistories);
        chatHistoryView.setAdapter(chatHistoryAdapter);
        chatHistoryView.setLayoutManager(new LinearLayoutManager(this));

        // Register image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            try {
                                handleImage(imageUri);
                            } catch (FileNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
        );

        //Food Preferences Options
        veganToggle = findViewById(R.id.veganToggle);
        glutenFreeToggle = findViewById(R.id.glutenFreeToggle);
        dairyFreeToggle = findViewById(R.id.dairyFreeToggle);

        calorieSeekBar = findViewById(R.id.calorieSeekBar);
        recipeCountSeekBar = findViewById(R.id.recipeCountSeekBar);


        // Set up Bottom navigation menu
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                Toast.makeText(this, "Home selected", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_favorites) {
                Toast.makeText(this, "Favorites selected", Toast.LENGTH_SHORT).show();
                return true;
            } else if (id == R.id.nav_profile) {
                Toast.makeText(this, "Profile selected", Toast.LENGTH_SHORT).show();
                return true;
            }

            return false;
        });
    }

    private void sendMessage() {
        String message = inputEditText.getText().toString().trim();
        if (!message.isEmpty()) {
            // Add user message to chat
            addMessageToChat(message, true);

            // Clear input field
            inputEditText.setText("");

            // Process the message with the API
            processTextQuery(message);
        }
    }

    private void addMessageToChat(String message, boolean isUser) {
        TextView messageBubble = new TextView(this);
        messageBubble.setText(message);
        messageBubble.setPadding(36, 36, 36, 36);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 24); // bottom margin

        if (isUser) {
            messageBubble.setBackgroundResource(R.drawable.bg_bubble_right);
            messageBubble.setTextColor(getResources().getColor(android.R.color.white));
            params.gravity = android.view.Gravity.END;
        } else {
            messageBubble.setBackgroundResource(R.drawable.bg_bubble_left);
            messageBubble.setTextColor(getResources().getColor(android.R.color.black));
            params.gravity = android.view.Gravity.START;
        }

        messageBubble.setLayoutParams(params);
        chatLayout.addView(messageBubble);

        // Scroll to bottom
        binding.chatScrollView.post(() -> binding.chatScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void processTextQuery(String query) {
        var client = new RecipeApiClient(API_ENDPOINT);

        new Thread(() -> {
            try {
                var req = new RecipeByChatRequest(query);
                var res = client.createRecipe(req);
                var generatedRecipes = res.getGenerated();

                // Add response to chat
                runOnUiThread(() -> addMessageToChat(generatedRecipes, false));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        addMessageToChat("Sorry, I couldn't process your request: " + e.getMessage(), false)
                );
            }
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            // Show confirmation dialog
            new AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Are you sure you want to logout?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        sessionManager.logoutUser();
                    })
                    .setNegativeButton("No", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onSelectPhoto(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    public void submitByChat(View v) {
        var content = inputEditText.getText().toString();
        inputEditText.setText("");

        if (content.isEmpty()) {
            return;
        }

        // Add user message to chat history
        chatHistories.add(ChatHistory.User(content));
        chatHistoryAdapter.notifyItemInserted(chatHistories.size() - 1);

        // Collect selected types
        List<String> types = new ArrayList<>();
        if (veganToggle.isChecked()) types.add("vegan");
        if (glutenFreeToggle.isChecked()) types.add("gluten free");
        if (dairyFreeToggle.isChecked()) types.add("dairy free");

        double maxCalories = calorieSeekBar.getProgress();
        int numberOfRecipes = recipeCountSeekBar.getProgress();
        String recipeRequest = RecipeHelper.getRecipeByChat(types, maxCalories, numberOfRecipes, content);

        var req = new RecipeByChatRequest(recipeRequest);
        performRequest(req);
    }

    private void saveHistory() {
        // Save chat history to a database or file
        // This is a placeholder for the actual implementation
        var trimmedHistories = chatHistories.stream().skip(2);
        var json = new Gson().toJson(trimmedHistories);
    }

    private void loadHistory() {
        // Load chat history from a database or file
        // This is a placeholder for the actual implementation
    }

    private <TReq> void performRequest(TReq req) {
        var client = new RecipeApiClient(API_ENDPOINT);

        // Add a loading message
        runOnUiThread(() -> addMessageToChat("Processing your image...", false));

        new Thread(() -> {
            try {
                var res = client.createRecipe(req);
                var generatedRecipes = res.getGenerated();

                runOnUiThread(() -> {
                    chatHistories.add(ChatHistory.Server(generatedRecipes));
                    chatHistoryAdapter.notifyItemInserted(chatHistories.size() - 1);
                });

                saveHistory();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    // Show error dialog
                    var errorMessage = "Error: " + e.getMessage();
                    DialogHelper.showErrorDialog(this, "Failed to request recipe", errorMessage);
                });
            }
        }).start();
    }

    private void handleImage(Uri imageUri) throws FileNotFoundException {
        try {
            var inputStream = getContentResolver().openInputStream(imageUri);
            var outputStream = new ByteArrayOutputStream();
            var buffer = new byte[1024];
            int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                inputStream.close();

                var imageBytes = outputStream.toByteArray();
                var base64Str = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                var req = new RecipeByImageRequest(base64Str);
                var res = client.createRecipe(req);

                var generatedRecipes = res.getGenerated();

                // Add response to chat
                runOnUiThread(() -> addMessageToChat(generatedRecipes, false));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        addMessageToChat("Sorry, I couldn't process your image: " + e.getMessage(), false)
                );
            }
        }).start();
    }
}