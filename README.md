# Galleria

Un'app galleria per Android ispirata all'app Foto di iOS, con un'interfaccia semplice e pulita.
Pensata per i telefoni con Android "stock" che hanno solo Google Foto.

## Scarica l'APK

L'APK firmato viene compilato automaticamente da GitHub Actions a ogni modifica e salvato in
[`release/Galleria.apk`](release/Galleria.apk).

Per installarlo: apri il file sul telefono, consenti l'installazione da "origini sconosciute"
quando richiesto, poi apri **Galleria** e consenti l'accesso a foto e video.

Requisiti: Android 11 o successivo.

## Funzioni

**Libreria**
- Vista *Anni*, *Mesi*, *Giorni* e *Tutte le foto*, con il selettore in basso come su iOS
- Le foto più recenti in fondo (come su iOS) oppure in alto, a scelta
- Pizzica la griglia per cambiare il numero di colonne
- Tieni premuto e trascina per selezionare più foto; barra di scorrimento rapido con il mese

**Visualizzatore**
- Apertura e chiusura con animazione di zoom dalla miniatura
- Pizzica o tocca due volte per ingrandire, scorri per passare alla foto successiva
- Trascina verso il basso per chiudere
- Tocca per nascondere i controlli (sfondo nero)
- Pellicola di miniature in basso, preferiti con il cuore, condivisione, eliminazione
- Pannello *Info*: data, fotocamera, obiettivo, ISO, apertura, tempo, dimensioni, luogo e mappa
- Video con barra di avanzamento, pausa e audio on/off
- GIF animate
- Menu: copia, duplica, nascondi, aggiungi ad album, presentazione, imposta come sfondo, apri con

**Modifica**
- *Regola*: esposizione, luminosità, contrasto, saturazione, calore, tinta, vignettatura, "Auto"
- *Filtri*: Vivido, Vivido caldo/freddo, Drammatico, Drammatico caldo/freddo, Mono, Argento, Noir
- *Ritaglia*: ruota, rifletti, proporzioni (libero, originale, quadrato, 16:9, 4:3, 4:5...)
- Tieni premuto sulla foto per vedere l'originale; la foto modificata viene salvata come copia

**Album**
- I miei album: Recenti, Preferiti, album personali (una foto può stare in più album) e cartelle
- Tipologie di media: Video, Selfie, Screenshot, Registrazioni schermo, Panoramiche, GIF, RAW
- Duplicati con il pulsante *Unisci*
- Nascosti ed Eliminati di recente protetti da impronta, volto o PIN
- Eliminati di recente usa il cestino di Android: gli elementi si possono recuperare per 30 giorni

**Per te**
- Ricordi creati automaticamente ("Il meglio del 2024", "Momenti di agosto", giornate speciali...)
- In questo giorno, negli anni passati
- Foto in evidenza
- Presentazione a schermo intero con dissolvenze ed effetto Ken Burns

**Cerca**
- Per album, cartella, nome del file, mese, anno, giorno della settimana, tipo
  (es. «video agosto», «screenshot 2024», «preferiti»)

**Integrazione con Android**
- Si apre dalle altre app ("Apri con") e dal pulsante della fotocamera
- Può essere usata per scegliere foto nelle altre app
- Tema chiaro e scuro automatico

## Suggerimento

In *Impostazioni › Gestione contenuti multimediali* puoi permettere a Galleria di spostare le
foto nel cestino senza la finestra di conferma di Android ogni volta.

## Compilare in locale

Serve l'Android SDK (piattaforma 35) e JDK 17:

```
./gradlew assembleRelease
```

L'APK si trova in `app/build/outputs/apk/release/app-release.apk`.
