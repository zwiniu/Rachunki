RACHUNKI 1.0 — projekt Android

1. Firebase Console: otwórz TEN SAM projekt Firebase co Rodzinne wizyty.
2. Dodaj NOWĄ aplikację Android o identyfikatorze pl.rodzina.rachunki.
3. Pobierz NOWY google-services.json i wstaw do app/google-services.json.
   Nie kopiuj starego pliku Rodzinnych wizyt (ma inny package_name).
4. Upewnij się, że Firestore ma reguły z Rodzinnych wizyt 1.4 (families/{fid}/data/{doc} dla członków).
5. Utwórz repo GitHub Rachunki, wgraj zawartość ZIP, Actions -> Rachunki APK -> Run workflow.
6. Pobierz artefakt Rachunki-APK i zainstaluj app-debug.apk na obu telefonach.
7. Zaloguj się istniejącym kontem e-mail/hasłem i wpisz kod rodziny z Rodzinnych wizyt.
   Jeśli konto już jest członkiem rodziny, dane rachunków zaczną się synchronizować.

Rachunki przechowywane są pod families/{familyId}/data/bills, a wizyty pod .../visits.
Powiadomienia lokalne: Android 13+ wymaga zgody na powiadomienia. Przypomnienia planowane są na 09:00 lokalnie.
Przypomnienia działają na urządzeniach, na których aplikacja pobrała aktualne rachunki.
UWAGA: równoczesne zapisy z dwóch telefonów mogą nadpisać ostatnią zmianę (jeden dokument JSON); kolejna wersja może używać osobnych dokumentów.
