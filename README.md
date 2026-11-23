# Galleria e Pura

Questo repository contiene due app Android:

- **[Pura](#pura--fotocamera-senza-ai)**: fotocamera con comandi manuali e **nessuna elaborazione AI**,
  pensata per il Nothing Phone (3a) ma compatibile con qualsiasi Android 12+ → [`release/Pura.apk`](release/Pura.apk)
- **[Galleria](#galleria)**: galleria in stile iOS → [`release/Galleria.apk`](release/Galleria.apk)

---

# Pura — fotocamera senza AI

L'app Fotocamera di Nothing applica sempre la sua elaborazione con AI (riconoscimento della scena,
HDR multi-scatto, nitidezza e levigatura aggressive) e non si può disattivare. Pura usa direttamente
le API Camera2 di Android: ottieni l'immagine del sensore con la sola elaborazione di base dell'ISP,
scelta da te.

## Installazione

Scarica [`release/Pura.apk`](release/Pura.apk) sul telefono, aprilo e consenti l'installazione da
"origini sconosciute". Al primo avvio consenti fotocamera e microfono (il microfono serve solo per
l'audio dei video). Foto e video vanno in `DCIM/Camera`, come quelli della fotocamera di sistema.

Pura risponde anche alle scorciatoie "apri fotocamera" di Android (anche dalla schermata di blocco):
se il sistema chiede quale fotocamera usare, scegli Pura. Alcuni telefoni però aprono sempre la
fotocamera di sistema con il doppio tocco del tasto di accensione: in quel caso metti l'icona di
Pura nel dock al posto di quella di Nothing.

## Cosa puoi regolare

**Modi**: FOTO (automatico), PRO (manuale) e VIDEO, scorrendo a destra/sinistra sull'anteprima.

**PRO** — una ghiera per ogni parametro, con "A" per tornare in automatico:
- **ISO** e **tempo di scatto** (fino al limite del sensore, anche diversi secondi). Se ne fissi uno
  solo, l'altro viene calcolato in automatico (priorità ISO o priorità tempi)
- **Messa a fuoco manuale** con **focus peaking** (bordi a fuoco evidenziati in rosso)
- **Bilanciamento del bianco**: preimpostazioni o temperatura in **Kelvin** (2000–10000 K),
  calibrata sul sensore
- **Compensazione dell'esposizione**
- **Zebra** sulle alte luci bruciate, **istogramma** dal vivo

**Foto**
- Proporzioni 4:3, 16:9, 1:1
- Risoluzione standard (12 MP) o **massima del sensore (50 MP)** se il telefono la rende disponibile
- **RAW (DNG) + JPEG**: i dati grezzi del sensore, senza alcuna elaborazione
- JPEG **Ultra HDR** se supportato, qualità JPEG regolabile (70–100)
- Flash (auto, sempre, torcia), timer 3/10 s, selfie specchiati (senza ricomprimere)
- Tocca per mettere a fuoco ed esporre, trascina in verticale per la compensazione,
  tieni premuto per il **blocco AE/AF**
- Zoom con i pulsanti degli obiettivi (0.6×, 1×, 2×…) o con il pizzico

**Elaborazione (nessuna AI)**
- **Riduzione rumore multi-scatto**: con poca luce Pura scatta 4–8 fotogrammi, li allinea e ne fa
  la media (rumore ridotto fino a ~2,8×, nessun dettaglio inventato). I soggetti in movimento
  vengono presi da un solo fotogramma, quindi niente scie. Attiva in automatico con poca luce
- *Naturale* (predefinito): riduzione rumore dell'ISP e nitidezza leggera
- *Standard*: la pipeline classica dell'ISP in alta qualità
- *Personalizzato*: riduzione rumore, nitidezza e curva tonale (standard, sRGB pura, piatta/Log)
- Correzione distorsione e stabilizzazione ottica attivabili/disattivabili

**Video**: 4K / 1080p / 720p, 30 o 60 fps (secondo il sensore), H.265 o H.264, bitrate alto,
stabilizzazione elettronica, audio, pausa, comandi manuali anche in video (pulsante **M**).

**Altro**: griglia (terzi, sezione aurea, 4×4), livella, tasti del volume come scatto o zoom,
suono di scatto disattivabile, posizione GPS opzionale (spenta di base), elenco delle fotocamere
che il telefono espone alle app esterne (*Impostazioni › Fotocamere del telefono*).

## Ottimizzazioni

- La fotocamera si apre in parallelo con l'interfaccia; le impostazioni si leggono in modo sincrono
- Cambiare parametri (ISO, zoom, flash…) aggiorna solo la richiesta al sensore, senza riaprire la sessione
- Focus peaking e zebra sono calcolati dalla GPU con uno shader (Android 13+), a costo zero per la CPU
- Istogramma ed esposizione semi-automatica leggono l'anteprima solo a bassa risoluzione e solo quando servono
- Scatto immediato quando la messa a fuoco è già pronta; i RAW vengono copiati subito e salvati in
  background, così si può scattare di nuovo senza attese
- Se una combinazione (es. 50 MP o RAW) non è supportata, Pura torna da sola alle impostazioni standard

## Nota sul Nothing Phone (3a)

Le app di terze parti possono usare solo le fotocamere che Nothing rende disponibili tramite Camera2.
Se un obiettivo (es. il teleobiettivo) o i 50 MP non compaiono, è una limitazione del sistema:
in *Impostazioni › Fotocamere del telefono* trovi l'elenco esatto di ciò che il telefono espone.

---

# Galleria

Un'app galleria per Android ispirata all'app Foto di iOS, con un'interfaccia semplice e pulita.
Pensata per i telefoni con Android "stock" che hanno solo Google Foto.

## Scarica l'APK

L'APK firmato viene compilato automaticamente da GitHub Actions a ogni modifica e salvato in
[`release/Galleria.apk`](release/Galleria.apk).

Per installarlo: apri il file sul telefono, consenti l'installazione da "origini sconosciute"
quando richiesto, poi apri **Galleria** e consenti l'accesso a foto e video.

Requisiti: Android 11 o successivo (effetto vetro completo da Android 12, rifrazione da Android 13).

## Novità della versione 2.0

**Mappa**
- Nuova scheda *Mappa*: tutte le foto e i video con posizione sulla mappa del mondo
- Le foto vicine si raggruppano in miniature con il numero di elementi; tocca per ingrandire
- Mappa di calore dei luoghi in cui hai scattato più foto
- Foglio trascinabile con le foto dell'area inquadrata e il nome del luogo
- Dal pannello *Info* di una foto: «Mostra sulla mappa di Galleria»
- Le posizioni vengono lette dai dati EXIF in background, salvate sul telefono e aggiornate solo
  per le foto nuove; la mappa usa OpenStreetMap (OpenFreeMap), senza account né chiavi

**Liquid Glass**
- Stile ispirato a iOS 26: barra delle schede fluttuante, pulsanti, menu e fogli di vetro con
  sfocatura, rifrazione ai bordi e riflessi (Android 12 o successivo)
- Nelle Impostazioni si può disattivare l'effetto per risparmiare batteria

**Prestazioni**
- Rendering della mappa su GPU con raggruppamento nativo dei punti
- Miniature della mappa caricate solo quando servono, con cache limitata
- Indicizzazione delle posizioni su thread a bassa priorità
- Griglia ottimizzata: nessun calcolo per miniatura durante lo scorrimento

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

Serve l'Android SDK (piattaforma 36) e JDK 17:

```
./gradlew assembleRelease
```

Gli APK si trovano in `app/build/outputs/apk/release/app-release.apk` (Galleria) e
`camera/build/outputs/apk/release/camera-release.apk` (Pura).
