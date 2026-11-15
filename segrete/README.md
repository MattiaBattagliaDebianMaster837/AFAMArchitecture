# Segrete di Mattia

<img src="icona.png" width="96" align="right">

Un roguelite d'azione a stanze per Android, nel genere dei dungeon crawler "twin-stick" (muoviti con
un dito, spara con l'altro). Il protagonista è Mattia, con la pixel art ricavata dalla sua foto.

> Mattia studia architettura. La notte prima della consegna, la sua tavola finale scompare nelle
> segrete sotto l'Accademia. Armato solo del suo inchiostro di china, scende nel buio per riprendersela.

## Scarica

L'APK firmato è in [`release/SegreteDiMattia.apk`](../release/SegreteDiMattia.apk).
Aprilo sul telefono e consenti l'installazione da "origini sconosciute". Requisiti: Android 7 o successivo.

## Comandi

| | Touch | Tastiera | Gamepad |
|---|---|---|---|
| Muoversi | stick a sinistra dello schermo | WASD | stick sinistro |
| Sparare | stick a destra (4 direzioni, o libera nelle opzioni) | frecce | stick destro / ABXY |
| Bomba | tasto bomba a destra | E | LB / LT |
| Oggetto attivo | tasto sopra la bomba | Spazio | RB / RT |
| Pausa | tasto in alto al centro, o "indietro" | Esc | Start |

Nelle opzioni: musica, effetti, vibrazione, mira a 4 direzioni o libera, comandi invertiti per mancini.

## Il gioco

- **5 piani** generati a caso a ogni partita: Le Cantine, L'Archivio Sommerso, Le Fondamenta,
  Le Catacombe e Il Cuore dell'Accademia. Ognuno ha il suo boss; sconfiggilo e salta nella botola.
- **Stanze**: le porte si chiudono finché non sconfiggi i nemici. Ci sono stanza del tesoro
  (dal 2° piano serve una chiave), negozio, stanza segreta (fai saltare i muri crepati con una bomba)
  e, dopo il boss, a volte la **stanza del patto**: oggetti potenti in cambio di cuori.
- **Vita**: cuori rossi e cuori d'inchiostro (blu, si consumano per primi).
- **Bottino**: monete, bombe, chiavi, forzieri (quelli dorati vogliono una chiave), rocce segnate.
- **36 oggetti passivi** a tema accademia (Caffè Doppio, Squadra a 45°, Compasso, Tavola A0,
  Modulor, Sezione Aurea, Cupola...) che cambiano le statistiche e i tiri: triplo, a ricerca,
  perforante, spettrale, che rimbalza, velenoso, infuocato, esplosivo...
- **8 oggetti attivi** che si ricaricano completando le stanze (Moka, Timbro dell'Accademia,
  Plotter, Clessidra, Biglietto dell'Autobus...).
- **9 tipi di nemici** (con varianti "campione" più forti) e **5 boss**: Re Macchia, Madre Sciame,
  La Tessitrice, Il Colosso e, alla fine, L'Ombra di Mattia.

Tutta la grafica è pixel art originale disegnata nel codice; musica ed effetti sono sintetizzati.

## Struttura

- `web/` il gioco (HTML5 canvas, JavaScript senza dipendenze). Si può provare anche nel browser:
  `cd segrete/web && python3 -m http.server` e apri `http://localhost:8000`.
- `android/` contenitore Android: una `WebView` a schermo intero che carica il gioco dagli asset.
- `tools/make_icons.py` rigenera le icone dell'app dallo sprite di Mattia.
- `build.sh` compila l'APK senza Gradle.

## Compilare

```
sudo apt install android-sdk-platform-23 aapt apksigner zipalign dalvik-exchange
./segrete/build.sh
```

Con un Android SDK completo basta impostare `ANDROID_HOME`. L'APK finisce in `release/SegreteDiMattia.apk`.
