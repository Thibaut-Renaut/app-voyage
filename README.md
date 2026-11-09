# Journal de Voyage — l'app Android

Portage natif (Kotlin + Jetpack Compose) de la PWA **Journal de Voyage** ([journalvoyage.netlify.app](https://journalvoyage.netlify.app/)), réalisé pour le TP Android du 9 novembre 2026 (M2 Informatique, Università di Corsica).

**Le même front et les mêmes fonctionnalités que la PWA** : la même page (en-tête, « Nouvelle étape de … », itinéraire numéroté, « Mes carnets de voyage », « Destinations inspirantes »), les mêmes fenêtres (créer, modifier, supprimer, changer de carnet), les mêmes textes, polices et couleurs, les mêmes données (carnets, étapes avec récit, position et photos, hors ligne d'abord, synchronisation différée). Plus :

- **une fonctionnalité impossible en PWA** : l'**enregistreur de trajet GPS**, un *foreground service* qui continue de tracer l'itinéraire écran éteint, app fermée et téléphone en Doze ;
- **la mesure** : trois événements Firebase Analytics, après consentement (RGPD).

La présentation (architecture, choix, natif, échecs, checklist) : [`docs/PRESENTATION.md`](docs/PRESENTATION.md).

## Construire et lancer

Prérequis : Android Studio (JDK 17+ inclus), SDK **API 37**, un émulateur avec les Google Play Services, ou un téléphone en débogage USB.

```bash
git clone <ce dépôt> && cd journal-voyage-android
# Créer local.properties (jamais commité) :
#   sdk.dir=C:/Users/<vous>/AppData/Local/Android/Sdk     (barres obliques, même sous Windows)
#   PWA_HOST=journalvoyage.netlify.app
#   SYNC_BASE_URL=https://httpbin.org/
./gradlew installDebug                 # construit et installe la version debug
adb shell am start -n corsica.universita.journalvoyage/.MainActivity
```

Ou dans Android Studio : *Open* → ce dossier → ▶ *Run 'app'*.

| Commande | Ce qu'elle fait |
|---|---|
| `./gradlew assembleDebug` | APK debug dans `app/build/outputs/apk/debug/` |
| `./gradlew testDebugUnitTest` | tests JVM : calculs du tracé (distance, dénivelé, GPX) |
| `./gradlew connectedDebugAndroidTest` | test de la migration Room v1 → v2, sur l'émulateur ou le téléphone (désinstalle l'app à la fin) |
| `./gradlew assembleRelease` | APK release, signée avec la clé de `local.properties` (`RELEASE_STORE_FILE`…), sinon avec la clé debug |

### Firebase (push et analytics)

`app/google-services.json` est celui du projet Firebase **`journal-voyage-f4981`** (application Android `corsica.universita.journalvoyage`, Google Analytics activé). Ce n'est pas un secret : la clé identifie le projet, elle ne donne pas accès aux données.

- Voir les événements en direct : `adb shell setprop debug.firebase.analytics.app corsica.universita.journalvoyage`, relancer l'app, puis console Firebase › Analytics › **DebugView**.
- Tester un push : menu ⋮ › *Copier mon jeton FCM*, puis console Firebase › Messaging › *Nouvelle campagne* › *Envoyer un message de test* ; donnée personnalisée `etapeId` pour ouvrir une étape.

## Les versions

| Version | versionCode | Base Room | Contenu |
|---|---|---|---|
| 1.0 (tag `v1.0`) | 1 | v1 : `voyages`, `etapes`, `photos` | le socle |
| 2.0 | 2 | v2 : + `trajets`, `track_points` (`MIGRATION_1_2`) | l'enregistreur de trajet |

Tester la mise à jour par-dessus l'ancienne version, sans désinstaller :

```bash
git checkout v1.0 && ./gradlew installDebug      # la 1.0 ; créer des étapes, dont une hors ligne
git checkout main && ./gradlew installDebug      # la 2.0 par-dessus : la migration tourne, rien n'est perdu
```

## Architecture en une image

```
UI Compose (écrans)  ──événements──▶  ViewModel (StateFlow, SavedStateHandle)
        ▲                                   │
        └──────── état observé ─────────────┤
                                            ▼
                               JournalRepository  (source de vérité unique)
                    ┌───────────────┬───────┴────────┬────────────────┐
                    ▼               ▼                ▼                ▼
               Room (journal.db)  DataStore     PhotoStore        Retrofit/OkHttp
               voyages, étapes,   voyage actif, (filesDir/photos)  POST httpbin.org/post
               photos, trajets    consentement,
                                  jeton FCM
   Hors des écrans :  SyncWorker (WorkManager)  ·  TrackingService (foreground, location)
                      JournalMessagingService (FCM)  — tous passent par le repository.
```

## Arborescence

```
app/src/main/java/corsica/universita/journalvoyage/
  JournalApp.kt            Application + JournalContainer (l'injection « à la main »)
  MainActivity.kt          l'unique Activity : intents (notification, partage, App Link)
  data/                    Repository, modèles, PhotoStore
    local/                 Room (entités, DAO, base + migration), DataStore (Settings)
    remote/                Retrofit (SyncApi, client OkHttp)
    location/              position ponctuelle (formulaire d'étape)
  tracking/                LA FONCTIONNALITÉ NATIVE : TrackingService, TrackMath (calculs, GPX)
  work/SyncWorker.kt       l'outbox
  notif/                   canaux, notifications, service FCM
  analytics/Analytics.kt   les trois événements, le consentement
  ui/home/                 la page unique de la PWA : HomeScreen, ses sections, ses fenêtres, HomeViewModel
  ui/common/Pwa.kt         un composable par classe CSS de la PWA (boutons, champs, modales, pastilles)
  ui/theme/                couleurs, rayons et polices de la PWA (Fraunces, Plus Jakarta Sans : res/font, licence OFL)
  ui/etape/, ui/trajet/    les écrans natifs : détail d'une étape (partage, carte), trajet GPS
  ui/permissions/          PermissionFlow : la permission jusqu'au refus définitif
app/schemas/               le schéma Room de chaque version (1.json, 2.json)
app/src/test/              TrackMathTest (JVM)
app/src/androidTest/       MigrationTest (appareil)
docs/                      PRESENTATION.md, captures d'écran
```
