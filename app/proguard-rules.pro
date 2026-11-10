# Project-specific R8 rules. Libraries ship their own rules, so this is empty for now.

# Credential Manager loads its Google Play services provider by reflection.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
