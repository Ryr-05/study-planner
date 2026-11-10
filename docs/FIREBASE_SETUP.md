# Sign-in and live sync setup

**Status: done.** Project `noctil-2333e` (Firestore in asia-southeast1, Singapore). The config is stored as the
`GOOGLE_SERVICES_JSON` GitHub secret and written into the build by CI. The steps below are kept for reference.

## Security rules in use
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## Original steps

Noctil already saves everything on your phone and can back up to a file (Me > Backup).
**Live sync between devices** and **Google sign-in** need a free Firebase project that belongs
to you. Only you can create it, because it lives in your Google account.

Once you've done the steps below, send the file from step 6 and the sign-in and sync code
gets switched on. The free Spark plan is plenty; no credit card is needed.

## 1. Create the project
1. Go to **https://console.firebase.google.com** and sign in with your Google account.
2. Click **Create a project**. Name it `Noctil`.
3. Turn **off** Google Analytics (not needed). Click **Create project**.

## 2. Add the Android app
1. On the project page, click the **Android** icon ("Add app").
2. **Android package name:** `io.github.ryr05.noctil`
3. **App nickname:** `Noctil`
4. **Debug signing certificate SHA-1:** paste this (it identifies the APKs GitHub builds):
   ```
   25:6D:50:46:16:60:45:27:B3:BF:21:A5:5A:F5:7E:62:E6:AA:B5:DD
   ```
5. Click **Register app**.

## 3. Add the SHA-256 too
1. Click the gear icon > **Project settings** > scroll to **Your apps** > Noctil.
2. Click **Add fingerprint** and paste:
   ```
   E3:5A:70:F7:F4:4B:37:91:28:09:02:B6:1D:64:8B:89:AE:B9:9D:CA:14:8A:50:C3:80:EC:59:BB:91:A1:01:E1
   ```

## 4. Turn on Google sign-in
1. Left menu: **Build > Authentication** > **Get started**.
2. **Sign-in method** tab > **Google** > **Enable**.
3. Pick your email as the support email. Click **Save**.

## 5. Create the database
1. Left menu: **Build > Firestore Database** > **Create database**.
2. Pick a location close to you (you can't change it later).
3. Choose **Start in production mode**. Click **Create**.

## 6. Download the config file
1. Gear icon > **Project settings** > **Your apps** > Noctil.
2. Click **google-services.json** to download it.
3. Send that file over (or drop it into this project's `app/` folder).

That file isn't a password, but keep it out of public places anyway. It will be stored as a
GitHub secret, not committed to the repo.

## What happens next
- Me > **Sign in with Google** appears. Signing in stays optional.
- Your tasks, lists, habits and timetable sync across every phone you sign in on.
- Everything keeps working offline, and changes sync when you're back online.
