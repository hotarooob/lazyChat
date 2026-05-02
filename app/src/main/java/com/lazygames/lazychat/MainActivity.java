package com.lazygames.lazychat;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "lazychat_prefs";
    private static final String KEY_USERNAME = "username";

    private final int GREEN = Color.rgb(7, 94, 84);
    private final int LIGHT_GREEN = Color.rgb(37, 211, 102);
    private final int CHAT_BG = Color.rgb(236, 229, 221);
    private final int SENT_BG = Color.rgb(220, 248, 198);
    private final int RECEIVED_BG = Color.WHITE;
    private final int TEXT_DARK = Color.rgb(30, 30, 30);
    private final int TEXT_MUTED = Color.rgb(110, 110, 110);

    private FirebaseFirestore db;
    private SharedPreferences prefs;
    private FrameLayout root;
    private String currentUser;
    private ListenerRegistration usersListener;
    private ListenerRegistration messagesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        root = new FrameLayout(this);
        setContentView(root);

        currentUser = prefs.getString(KEY_USERNAME, null);
        showSplashThenNext();
    }

    @Override
    protected void onDestroy() {
        removeListeners();
        super.onDestroy();
    }

    private void removeListeners() {
        if (usersListener != null) {
            usersListener.remove();
            usersListener = null;
        }
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }

    private void showSplashThenNext() {
        root.removeAllViews();
        FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(GREEN);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView logo = new TextView(this);
        logo.setText("lazyChat");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(42);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("by lazyGames");
        sub.setTextColor(Color.argb(210, 255, 255, 255));
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(10), 0, 0);

        box.addView(logo);
        box.addView(sub);
        splash.addView(box, frameParams(-1, -1));
        root.addView(splash);

        splash.setAlpha(0f);
        splash.animate().alpha(1f).setDuration(450).withEndAction(() -> splash.postDelayed(() -> {
            splash.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                if (currentUser == null || currentUser.trim().isEmpty()) showLogin();
                else {
                    registerUser(currentUser, false);
                    showUsers();
                }
            }).start();
        }, 700)).start();
    }

    private void showLogin() {
        removeListeners();
        root.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        page.setPadding(dp(28), dp(28), dp(28), dp(28));
        page.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Welcome to lazyChat");
        title.setTextSize(30);
        title.setTextColor(GREEN);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter a username to start chatting");
        subtitle.setTextSize(15);
        subtitle.setTextColor(TEXT_MUTED);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(10), 0, dp(24));

        EditText username = new EditText(this);
        username.setHint("Username");
        username.setSingleLine(true);
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        username.setTextColor(TEXT_DARK);
        username.setTextSize(18);
        username.setPadding(dp(16), 0, dp(16), 0);
        username.setBackground(roundStroke(Color.WHITE, Color.rgb(210, 210, 210), dp(14), 1));

        Button start = new Button(this);
        start.setText("Start Chatting");
        start.setTextColor(Color.WHITE);
        start.setTextSize(17);
        start.setAllCaps(false);
        start.setBackground(round(LIGHT_GREEN, dp(24)));

        page.addView(title, linearParams(-1, -2));
        page.addView(subtitle, linearParams(-1, -2));
        LinearLayout.LayoutParams inputLp = linearParams(-1, dp(54));
        inputLp.setMargins(0, 0, 0, dp(16));
        page.addView(username, inputLp);
        page.addView(start, linearParams(-1, dp(54)));
        root.addView(page, frameParams(-1, -1));

        start.setOnClickListener(v -> {
            String raw = username.getText().toString().trim();
            String clean = normalizeUsername(raw);
            if (clean.length() < 3) {
                toast("Username must be at least 3 characters");
                return;
            }
            currentUser = clean;
            prefs.edit().putString(KEY_USERNAME, currentUser).apply();
            registerUser(currentUser, true);
            hideKeyboard(username);
            showUsers();
        });
    }

    private void registerUser(String username, boolean showToast) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", username);
        data.put("searchName", username.toLowerCase(Locale.US));
        data.put("lastSeen", FieldValue.serverTimestamp());
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection("users").document(username).set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> { if (showToast) toast("Logged in as " + username); })
                .addOnFailureListener(e -> toast("Firebase error: " + e.getMessage()));
    }

    private void showUsers() {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        root.removeAllViews();

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);

        LinearLayout header = header("lazyChat", currentUser, true);
        page.addView(header, linearParams(-1, dp(76)));

        TextView hint = new TextView(this);
        hint.setText("Tap a user to start a chat. Ask your friend to open the same APK and enter a username.");
        hint.setTextColor(TEXT_MUTED);
        hint.setTextSize(13);
        hint.setPadding(dp(16), dp(10), dp(16), dp(10));
        page.addView(hint, linearParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, linearParams(-1, 0, 1));
        root.addView(page, frameParams(-1, -1));

        if (usersListener != null) usersListener.remove();
        usersListener = db.collection("users").orderBy("searchName", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        toast("Users error: " + error.getMessage());
                        return;
                    }
                    list.removeAllViews();
                    if (snap == null || snap.isEmpty()) {
                        list.addView(emptyText("No users yet. Open the app on another phone with a different username."));
                        return;
                    }
                    int count = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String name = doc.getString("name");
                        if (name == null || name.equals(currentUser)) continue;
                        count++;
                        list.addView(userRow(name));
                    }
                    if (count == 0) {
                        list.addView(emptyText("No other users yet. Use another device and enter another username."));
                    }
                });
    }

    private View userRow(String username) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundColor(Color.WHITE);

        TextView avatar = new TextView(this);
        avatar.setText(username.substring(0, 1).toUpperCase(Locale.US));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(20);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(GREEN, dp(24)));
        row.addView(avatar, linearParams(dp(48), dp(48)));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setPadding(dp(14), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText(username);
        name.setTextColor(TEXT_DARK);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);

        TextView sub = new TextView(this);
        sub.setText("Tap to chat");
        sub.setTextColor(TEXT_MUTED);
        sub.setTextSize(13);

        textBox.addView(name);
        textBox.addView(sub);
        row.addView(textBox, linearParams(0, -2, 1));
        row.setOnClickListener(v -> showChat(username));
        return row;
    }

    private void showChat(String otherUser) {
        if (usersListener != null) {
            usersListener.remove();
            usersListener = null;
        }
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
        root.removeAllViews();

        String chatId = chatIdFor(currentUser, otherUser);
        ensureChatDoc(chatId, otherUser);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(CHAT_BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(12), dp(8));
        top.setBackgroundColor(GREEN);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(38);
        back.setGravity(Gravity.CENTER);
        top.addView(back, linearParams(dp(44), -1));

        TextView avatar = new TextView(this);
        avatar.setText(otherUser.substring(0, 1).toUpperCase(Locale.US));
        avatar.setTextColor(GREEN);
        avatar.setTextSize(18);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(round(Color.WHITE, dp(22)));
        top.addView(avatar, linearParams(dp(44), dp(44)));

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setPadding(dp(12), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(otherUser);
        name.setTextColor(Color.WHITE);
        name.setTextSize(18);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        TextView status = new TextView(this);
        status.setText("online in lazyChat");
        status.setTextColor(Color.argb(210, 255, 255, 255));
        status.setTextSize(12);
        textBox.addView(name);
        textBox.addView(status);
        top.addView(textBox, linearParams(0, -2, 1));
        page.addView(top, linearParams(-1, dp(72)));

        ScrollView scroll = new ScrollView(this);
        LinearLayout messagesBox = new LinearLayout(this);
        messagesBox.setOrientation(LinearLayout.VERTICAL);
        messagesBox.setPadding(dp(10), dp(10), dp(10), dp(10));
        scroll.addView(messagesBox);
        page.addView(scroll, linearParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(8), dp(8), dp(8), dp(8));
        composer.setBackgroundColor(Color.rgb(245, 245, 245));

        EditText input = new EditText(this);
        input.setHint("Message");
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setTextSize(16);
        input.setBackground(round(Color.WHITE, dp(24)));
        input.setPadding(dp(16), 0, dp(16), 0);
        composer.addView(input, linearParams(0, dp(48), 1));

        Button send = new Button(this);
        send.setText("➤");
        send.setTextColor(Color.WHITE);
        send.setTextSize(20);
        send.setAllCaps(false);
        send.setBackground(round(LIGHT_GREEN, dp(24)));
        LinearLayout.LayoutParams sendLp = linearParams(dp(54), dp(48));
        sendLp.setMargins(dp(8), 0, 0, 0);
        composer.addView(send, sendLp);
        page.addView(composer, linearParams(-1, dp(66)));

        root.addView(page, frameParams(-1, -1));

        back.setOnClickListener(v -> showUsers());
        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            input.setText("");
            sendMessage(chatId, otherUser, text);
        });

        messagesListener = db.collection("chats").document(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, error) -> {
                    if (error != null) {
                        toast("Messages error: " + error.getMessage());
                        return;
                    }
                    messagesBox.removeAllViews();
                    if (snap == null || snap.isEmpty()) {
                        messagesBox.addView(centerHint("No messages yet. Say hi 👋"));
                    } else {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String sender = doc.getString("sender");
                            String text = doc.getString("text");
                            Timestamp ts = doc.getTimestamp("createdAt");
                            boolean mine = currentUser.equals(sender);
                            messagesBox.addView(messageBubble(text == null ? "" : text, mine, ts));
                        }
                    }
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                });
    }

    private void ensureChatDoc(String chatId, String otherUser) {
        Map<String, Object> data = new HashMap<>();
        data.put("users", Arrays.asList(currentUser, otherUser));
        data.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("chats").document(chatId).set(data, SetOptions.merge());
    }

    private void sendMessage(String chatId, String otherUser, String text) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("sender", currentUser);
        msg.put("receiver", otherUser);
        msg.put("text", text);
        msg.put("createdAt", FieldValue.serverTimestamp());

        DocumentReference chatRef = db.collection("chats").document(chatId);
        chatRef.collection("messages").add(msg).addOnFailureListener(e -> toast("Send failed: " + e.getMessage()));

        Map<String, Object> chatUpdate = new HashMap<>();
        chatUpdate.put("users", Arrays.asList(currentUser, otherUser));
        chatUpdate.put("lastMessage", text);
        chatUpdate.put("lastSender", currentUser);
        chatUpdate.put("updatedAt", FieldValue.serverTimestamp());
        chatRef.set(chatUpdate, SetOptions.merge());
    }

    private View messageBubble(String text, boolean mine, Timestamp timestamp) {
        LinearLayout outer = new LinearLayout(this);
        outer.setGravity(mine ? Gravity.RIGHT : Gravity.LEFT);
        outer.setPadding(0, dp(3), 0, dp(3));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(6));
        bubble.setBackground(round(mine ? SENT_BG : RECEIVED_BG, dp(14)));

        TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(TEXT_DARK);
        body.setTextSize(16);
        body.setLineSpacing(2, 1.0f);

        TextView time = new TextView(this);
        time.setText(formatTime(timestamp));
        time.setTextColor(TEXT_MUTED);
        time.setTextSize(10);
        time.setGravity(Gravity.RIGHT);
        time.setPadding(0, dp(4), 0, 0);

        bubble.addView(body);
        bubble.addView(time);
        LinearLayout.LayoutParams bubbleLp = linearParams(-2, -2);
        bubbleLp.width = dp(285);
        outer.addView(bubble, bubbleLp);
        return outer;
    }

    private LinearLayout header(String title, String subtitle, boolean showLogout) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(8), dp(12), dp(8));
        header.setBackgroundColor(GREEN);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(23);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subView = new TextView(this);
        subView.setText("@" + subtitle);
        subView.setTextColor(Color.argb(210, 255, 255, 255));
        subView.setTextSize(13);

        texts.addView(titleView);
        texts.addView(subView);
        header.addView(texts, linearParams(0, -2, 1));

        if (showLogout) {
            TextView logout = new TextView(this);
            logout.setText("Logout");
            logout.setTextColor(Color.WHITE);
            logout.setTextSize(14);
            logout.setGravity(Gravity.CENTER);
            logout.setPadding(dp(10), dp(8), dp(10), dp(8));
            logout.setOnClickListener(v -> {
                prefs.edit().remove(KEY_USERNAME).apply();
                currentUser = null;
                showLogin();
            });
            header.addView(logout, linearParams(-2, -2));
        }
        return header;
    }

    private TextView emptyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_MUTED);
        view.setTextSize(15);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(28), dp(70), dp(28), dp(28));
        return view;
    }

    private TextView centerHint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT_MUTED);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(20), dp(30), dp(20), dp(30));
        return view;
    }

    private String chatIdFor(String a, String b) {
        List<String> users = new ArrayList<>();
        users.add(a);
        users.add(b);
        Collections.sort(users);
        return safeId(users.get(0)) + "_" + safeId(users.get(1));
    }

    private String normalizeUsername(String input) {
        String value = input == null ? "" : input.trim().toLowerCase(Locale.US);
        value = value.replaceAll("[^a-z0-9_]+", "_");
        value = value.replaceAll("_+", "_");
        if (value.startsWith("_")) value = value.substring(1);
        if (value.endsWith("_")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private String safeId(String input) {
        return normalizeUsername(input);
    }

    private String formatTime(Timestamp timestamp) {
        if (timestamp == null) return "sending";
        Date date = timestamp.toDate();
        return new SimpleDateFormat("HH:mm", Locale.US).format(date);
    }

    private void hideKeyboard(View view) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private FrameLayout.LayoutParams frameParams(int w, int h) {
        return new FrameLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams linearParams(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams linearParams(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable roundStroke(int color, int strokeColor, int radius, int strokeDp) {
        GradientDrawable drawable = round(color, radius);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }
}
