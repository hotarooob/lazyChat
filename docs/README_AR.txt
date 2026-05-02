lazyChat V1

المواصفات:
- Package: com.lazygames.lazychat
- Firebase project متصل عبر google-services.json
- Database: Cloud Firestore
- Login: Username only
- Style: WhatsApp-like
- Native Android Java بدون Android Studio إلزامي

طريقة التشغيل عبر GitHub Actions:
1) فك ضغط المشروع.
2) ارفع محتويات مجلد lazyChat_Android إلى GitHub repo.
3) افتح تبويب Actions.
4) شغل Build lazyChat APK.
5) بعد انتهاء البناء، حمل artifact باسم lazyChat-debug-apk.
6) فك ضغطه وثبت app-debug.apk.

Firestore collections:
users/{username}
  name
  searchName
  createdAt
  lastSeen

chats/{chatId}
  users
  lastMessage
  lastSender
  updatedAt

chats/{chatId}/messages/{messageId}
  sender
  receiver
  text
  createdAt

قواعد Firestore للتجربة فقط:
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if true;
    }
    match /chats/{chatId} {
      allow read, write: if true;
      match /messages/{messageId} {
        allow read, write: if true;
      }
    }
  }
}

ملاحظة أمنية:
هذه القواعد للتجربة فقط. قبل نشر التطبيق للعامة يجب إضافة Auth حقيقي وقواعد أمان.
