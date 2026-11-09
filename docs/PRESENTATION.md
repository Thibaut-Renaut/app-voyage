# Journal de Voyage Android — préparer la présentation

Le plan suit la slide 8 du TP : **la démo, l'architecture, les choix, le natif, les échecs**. Chaque section dit quoi montrer et quoi dire. À la fin : la checklist de la slide 9, avec la commande qui prouve chaque point.

---

## 1. La démo (5 minutes, dans cet ordre)

1. **Lancement — la même page que la PWA.** Splash, puis l'accueil : « Votre Journal, *de Voyage.* », « Nouvelle étape de *Échappée en Corse* », l'itinéraire numéroté (Bavella, Piana, Palombaggia), « Mes carnets de voyage », « Destinations inspirantes ». Mêmes textes, mêmes polices (Fraunces, Plus Jakarta Sans), mêmes couleurs. Le bandeau de consentement : *Accepter*. Montrer la PWA à côté, dans Chrome.
2. **Les carnets.** *Changer de voyage* → *Créer un nouveau voyage* → il devient le carnet actif (toast « Nouveau carnet … créé ! »). *Modifier le carnet*, puis supprimer un carnet (confirmation) ; essayer de supprimer le dernier : « Impossible de supprimer le seul carnet existant. »
3. **Mode avion → nouvelle étape.** La pastille « Mode hors ligne » apparaît en haut. Titre, récit, *Ma position* (dialogue de permission, « pendant l'utilisation »), *Prendre une photo* (CameraX), *Enregistrer cette étape →*. Toast « Étape enregistrée hors ligne — synchronisation différée », pastille ambre « En attente de sync ».
4. **Fermer l'app** (la balayer des récents), **couper le mode avion.** Sans rouvrir l'app : la notification « Étapes synchronisées » arrive. On rouvre : « ✓ Synchronisé ».
5. **Le natif.** Section « Trajets GPS » (sous l'itinéraire) : *Enregistrer un trajet*. Marcher (ou rejouer un itinéraire dans l'émulateur : *Extended controls › Location › Routes*). Le tracé se dessine en direct. **Éteindre l'écran, balayer l'app des récents**, marcher encore. Rallumer : la notification « Trajet en cours » a son chronomètre et ses points. *Arrêter* depuis la notification. Rouvrir : le trajet terminé, ses stats, *Partager le GPX*.
6. **Le lien.** Ouvrir `https://journalvoyage.netlify.app/` depuis une autre app (Messages, Notes) : c'est l'app qui s'ouvre, pas Chrome.
7. **Analytics.** La console Firebase › *DebugView* : `first_etape_created`, `trajet_started`, `trajet_completed` apparaissent en direct. Sans Firebase : `adb logcat -s Journal` montre chaque événement.

## 2. L'architecture : d'où viennent les données

> « Une seule source de vérité, le repository ; l'écran n'affiche que ce que Room contient. »

- **UI Compose → ViewModel → Repository → sources.** L'écran ne garde aucun état important : il observe un `StateFlow` du ViewModel et lui envoie des événements.
- **Room est la vérité.** Écrire, c'est écrire dans Room ; Room réémet ses `Flow`, et tous les écrans abonnés se mettent à jour, y compris quand c'est le `SyncWorker` (badge vert) ou le `TrackingService` (le tracé) qui écrit. Les écrans et les services ne se parlent jamais directement.
- **Trois composants sans écran** passent par le même repository : `SyncWorker` (l'outbox), `TrackingService` (les trajets), `JournalMessagingService` (le push). D'où le `JournalContainer`, créé dans `Application.onCreate` : il existe avant n'importe lequel d'entre eux, même quand l'OS relance le process pour un seul worker.

La correspondance avec la PWA :

| Dans la PWA | Dans l'app | Fichier |
|---|---|---|
| La page unique `index.html` + `app.js` | `HomeScreen` (une `LazyColumn`, mêmes sections dans le même ordre) + `HomeViewModel` | `ui/home/` |
| Les classes CSS (`.btn--primary`, `.form__input`, `.journal-entry__dot`…) | un composable par classe, mêmes couleurs, rayons et polices | `ui/common/Pwa.kt`, `ui/theme/` |
| Les modales (créer, modifier, supprimer, changer de carnet) | `Dialog` Compose ; la fenêtre ouverte est dans le `SavedStateHandle` (survit à la rotation) | `ui/home/VoyageDialogs.kt` |
| `showToast()`, `showNetworkStatus()` (événements `online`/`offline`) | toasts via un `Channel` ; `ConnectivityManager.NetworkCallback` | `HomeScreen.kt`, `data/NetworkMonitor.kt` |
| `renderJournal()` | Compose + ViewModel | `ui/home/HomeSections.kt` |
| IndexedDB `voyages`, `etapes` | Room : tables `voyages`, `etapes`, `photos` | `data/local/Entities.kt` |
| object store `outbox` | colonne `etapes.synced` (0 = à envoyer) | `Daos.kt` (`unsynced()`) |
| Background Sync + événement `online` | WorkManager : travail unique, contrainte réseau | `JournalRepository.enqueueSync()`, `SyncWorker` |
| `localStorage` (`pwa-active-voyage-id`) | DataStore | `data/local/Settings.kt` |
| Push API + Service Worker | FCM + `FirebaseMessagingService` | `notif/JournalMessagingService.kt` |
| Permissions du navigateur | permissions runtime, jusqu'au refus définitif | `ui/permissions/PermissionFlow.kt` |
| `manifest.json` | `AndroidManifest.xml`, icône adaptive, splash | |
| L'URL | App Link vérifié (`assetlinks.json`) | manifest, `TPNOTE/.well-known/assetlinks.json` |
| `photos` en base64 dans IndexedDB | fichiers JPEG dans `filesDir/photos`, chemin en base | `data/PhotoStore.kt` |

**Ce qui ne change pas** : l'API (`POST httpbin.org/post`, le même `syncPayload`), les données de démo, l'URL. **Ce qui change** : l'arbitre. Le navigateur devient l'OS.

## 3. Les choix, chacun en une phrase

| Question | Réponse |
|---|---|
| **Room ou DataStore ?** | Room pour ce qui est une table (voyages, étapes, photos, trajets, points), qu'on interroge et qui migre ; DataStore pour quatre valeurs isolées (voyage actif, consentement, jeton FCM, jour de la dernière session). |
| **Les photos en base ?** | Non : le fichier dans `filesDir/photos` (privé, sans permission de stockage), le chemin en base. Supprimer l'étape efface aussi le fichier, sinon il reste orphelin. |
| **Quand part l'envoi ?** | Jamais depuis l'écran : l'étape est écrite dans Room, puis `enqueueUniqueWork("sync", KEEP)` avec une contrainte réseau. **L'OS choisit le moment** : app fermée ou tuée, il relance le process. 429/5xx/réseau → `retry` (30 s, 1 min, 2 min…), autre 4xx → `failure`. |
| **Le badge « en attente » ?** | Il vient de Room (`COUNT(*) WHERE synced = 0`), pas de WorkManager : c'est la donnée qui fait foi. |
| **Quelle permission, quand ?** | Au geste, jamais au lancement : position au bouton *Ma position* ou *Enregistrer un trajet* ; caméra au bouton *Prendre une photo* ; notifications au bouton du bandeau. Après un refus : explication ; après deux : les réglages. La galerie passe par le *Photo Picker* : **aucune** permission. |
| **Et sans la permission ?** | L'app marche : étape sans position, photo depuis la galerie, pas de trajet. |
| **Pourquoi pas `ACCESS_BACKGROUND_LOCATION` ?** | Le service est démarré app ouverte : la permission « pendant l'utilisation » couvre tout l'enregistrement. Demander l'arrière-plan serait plus intrusif, et refusé par le Play Store sans justification. |
| **Rotation et mort du process ?** | Le backstack de navigation (`rememberNavBackStack`) et les brouillons (`SavedStateHandle` : titre, récit, position, chemins des photos déjà prises) sont dans le Bundle que l'OS garde. |
| **La migration ?** | `version = 2`, `MIGRATION_1_2` (deux `CREATE TABLE` recopiés de `2.json`), `exportSchema` et `schemas/` dans Git, jamais `fallbackToDestructiveMigration`. Testée : `MigrationTest`, plus une vraie mise à jour 1.0 → 2.0 sur l'émulateur. |
| **Le push : le bon écran ?** | `PendingIntent` vers `MainActivity` avec l'extra `etapeId` ; `requestCode` différent par étape. La console Firebase envoie ses données personnalisées en **String** : `MainActivity` accepte Long et String. |
| **Le consentement ?** | Collecte coupée dans le manifest (`firebase_analytics_collection_enabled=false`), activée seulement après « Accepter », modifiable dans le menu ⋮. Aucune donnée personnelle dans les événements. |
| **Pourquoi pas Hilt ?** | Un conteneur à la main (`JournalContainer`) : quelques objets, tous visibles en un fichier. |

## 4. Le natif : l'enregistreur de trajet

**Ce que l'OS accorde.** Un *foreground service* de type `location` (`TrackingService`). L'app promet à l'OS un travail **visible** (notification permanente, chronomètre, bouton *Arrêter*). En échange, l'OS :

- garde le process en vie quand l'app est balayée des récents ;
- continue de livrer la position (fused location provider, haute précision, toutes les 5 s et seulement après 5 m de déplacement) écran éteint et en Doze ;
- ne demande que la permission « pendant l'utilisation », parce que le service démarre app ouverte.

**Pourquoi la PWA ne pouvait pas.** `navigator.geolocation.watchPosition()` s'arrête dès que l'onglet passe en arrière-plan ou que l'écran s'éteint, et un Service Worker n'a pas accès à la géolocalisation. Dans la grille des capacités des PWA (Jour 1), « géolocalisation en arrière-plan » est rouge.

**Comment c'est construit.**

- `TrackingService.start()` → `startForegroundService()` : promesse d'appeler `startForeground()` dans les 5 s. C'est la première chose que fait le service.
- Android 14+ vérifie la permission de localisation **dans** `startForeground()` : `SecurityException` attrapée → événement `trajet_failed`.
- Chaque position passe par un filtre (`TrackMath.accept` : précision > 50 m ou moins de 3 m du point précédent → ignorée), puis va dans Room en **une transaction** avec la nouvelle distance. L'écran observe `track_points` et se redessine.
- Le tracé est dessiné sur un `Canvas` (projection équirectangulaire, longitude × cos(latitude)) : pas de SDK de cartes, pas de clé d'API, ça marche hors ligne.
- `START_NOT_STICKY` : si l'OS tue le process, on ne relance pas un enregistrement dans le dos de l'utilisateur. Au démarrage suivant, le trajet resté « en cours » est clos à l'heure de son dernier point (`closeOrphanTrajets`).
- Export **GPX 1.1** (Strava, Komoot, OsmAnd) via `FileProvider`.

**Prouvé sur l'émulateur** (captures 06, 07, 09, 10, 13) : 19 points enregistrés app balayée des récents, écran éteint et en Doze forcé (`deviceidle force-idle`) ; arrêt depuis la notification app fermée ; rotation pendant l'enregistrement ; process tué en plein trajet, trajet clos au redémarrage.

**Le risque pris.** C'est la fonctionnalité qui négocie le plus avec l'OS : permission, type de service, Doze, mort du process, notification.

## 5. Les échecs, et comment ils ont été réparés

| Échec | Cause | Réparation |
|---|---|---|
| **Crash** `IllegalArgumentException: NavDisplay backstack cannot be empty` en tapant du texte hors d'un champ | La barre d'espace « clique » sur le bouton Retour qui a le focus. 1ᵉʳ clic : on revient à la liste. 2ᵉ clic : il arrive sur l'écran **en train de partir** (encore affiché pendant l'animation), dont le `removeLastOrNull()` retire la racine. Pile vide. Un double-tap rapide sur Retour fait pareil. | `pop(key)` : un écran ne retire que **sa propre** clé, et seulement si elle est au sommet. Même garde à l'aller (`push`). Rejoué : plus de crash. |
| `Error: La chaîne "--" n'est pas autorisée dans les commentaires` (build) | Un commentaire XML qui citait `--green-*` (les variables CSS de la PWA). | Reformulé. En XML, `--` est interdit dans un commentaire. |
| Les captures d'écran absentes du premier commit | Le `.gitignore` hérité de Carnet ignorait **tout** dossier `captures/`, donc `docs/captures/` aussi. | `/captures/` : seulement à la racine. |
| Pas de jeton FCM : `Please set a valid API key` | `google-services.json` factice (projet de départ du cours). | Vrai projet Firebase `journal-voyage-f4981` : jeton obtenu, événements Analytics envoyés (HTTP 204). |
| App Link `1024` au lieu de `verified` juste après le déploiement de `assetlinks.json` | Le vérifieur passe par l'API Digital Asset Links de Google, qui avait encore l'ancienne version en cache (`maxAge` ≈ 27 min). | Attendre l'expiration, puis `pm verify-app-links --re-verify` → `verified`. |
| Dénivelé de 4 m sur une montée simulée de 40 m | L'émulateur bloque l'altitude à 7,5 m quoi qu'envoie `geo fix`. L'app enregistre ce qu'elle reçoit (vérifié dans le GPX). | Limite de l'émulateur. Le calcul est couvert par `TrackMathTest` (seuil de 3 m contre le bruit GPS). À montrer sur un vrai téléphone. |
| Push ouvert sur l'accueil et non sur l'étape | Le message de test avait été envoyé sans la donnée personnalisée `etapeId`. | Renvoyé avec `etapeId` = 4 → l'étape s'ouvre. `MainActivity` logue désormais les clés reçues (`extras : [...]`) pour le voir tout de suite. |
| Position lente sur le téléphone (≈ 30 s, à 100 m près) | En intérieur et en mode avion : pas de Wi-Fi ni d'antenne pour aider le GPS. | L'indicateur de chargement l'affiche ; en démo, se placer près d'une fenêtre. |
| **Le front ne ressemblait pas à la PWA** (1ʳᵉ version : liste de carnets → écran voyage → écran formulaire, style Material par défaut) | J'avais porté les données et les mécanismes, pas l'interface. Manquaient aussi : « Changer de voyage », les règles (dernier carnet, description obligatoire), les toasts, l'indicateur réseau, les destinations. | Refonte : `HomeScreen` reprend `index.html` section par section, un composable par classe CSS, les polices de la PWA embarquées (`res/font`), ses textes mot pour mot. |
| Les champs d'une fenêtre restaient vides malgré la saisie (même au clavier) | Pour vider un brouillon j'appelais `SavedStateHandle.remove(clé)` : cela **détache** le `StateFlow` rendu par `getStateFlow()`, les saisies suivantes n'étaient plus émises. | Remettre la valeur par défaut (`saved[clé] = ""`), jamais `remove()`. Commenté dans `HomeViewModel`. |
| Pastille « Connexion rétablie » alors que la synchro ne partait pas (émulateur) | Le Wi-Fi avait `INTERNET` mais pas `VALIDATED` ; WorkManager exige un réseau validé, mon indicateur non. | L'indicateur exige aussi `VALIDATED` (et écoute `onCapabilitiesChanged`) : il dit la même chose que WorkManager. |
| Vitesse moyenne de 20 à 44 km/h « en marchant » | La marche simulée envoie un point toutes les 2,5 s, espacés de ~50 m. | Normal en simulation ; réaliste sur le terrain. |

## Checklist de la slide 9, et sa preuve

| Point | Comment le prouver | État (émulateur, 9 nov.) |
|---|---|---|
| Mode avion : l'app s'ouvre, affiche ses données, dit ce qui est indisponible | `adb shell cmd connectivity airplane-mode enable`, puis étape → badge « En attente » | ✅ capture 22 |
| Retour du réseau : l'action hors ligne part seule | App tuée (`am kill`), `airplane-mode disable`, puis `adb logcat -s Journal` : `SyncWorker … 1 étape(s) envoyée(s)`, nouveau PID | ✅ |
| Mise à jour par-dessus l'ancienne version | `adb install -r demo/journal-v1.0.apk`, créer des données, puis `adb install -r` de la 2.0 : les 4 étapes sont là ; `connectedDebugAndroidTest` | ✅ |
| Rotation, mort du process : rien ne se perd | Formulaire rempli + photo → Home → `adb shell am kill …` → retour : tout est là. Rotation pendant un trajet. | ✅ |
| Notification reçue app tuée ; le clic ouvre le bon écran | Console Firebase › Messaging › « Envoyer un message de test » (jeton : menu ⋮ › *Copier mon jeton FCM*), données personnalisées `etapeId` = 4. Sans Firebase : `adb shell am start -n corsica.universita.journalvoyage/.MainActivity --es etapeId 4 --es google.message_id test` | ✅ sur le Galaxy S21 (Android 15) : message de test reçu app tuée, clic → l'app s'ouvre (`notif_clicked`) ; avec `etapeId` = 4 → l'étape 4 |
| Permission refusée deux fois : réglages proposés, l'app marche sans | `pm revoke` des deux localisations, refuser, explication, refuser → « Ouvrir les réglages » | ✅ |
| App Link : `pm get-app-links` → verified | `adb shell pm verify-app-links --re-verify corsica.universita.journalvoyage` puis `adb shell pm get-app-links corsica.universita.journalvoyage` | ✅ `verified` ; `am start -a VIEW -d https://journalvoyage.netlify.app/` ouvre l'app |
| Fonctionnalité native démontrable | Trajet écran éteint, app balayée, Doze ; arrêt depuis la notification | ✅ captures 06, 07, 09 |
| Analytics : les trois événements en direct | `adb shell setprop debug.firebase.analytics.app corsica.universita.journalvoyage`, puis Firebase › DebugView. Sans Firebase : `adb logcat -s Journal \| grep Analytics` | ✅ `first_etape_created`, `trajet_started`, `trajet_completed` envoyés (FA-SVC : `Successful upload … 204`) ; rien avant « Accepter » |
| Dépôt Git poussé, README à jour | | dépôt local prêt, à pousser |

## Commandes utiles pendant la démo

```bash
adb logcat -s Journal                                        # tout ce que fait l'app
adb shell cmd connectivity airplane-mode enable|disable      # mode avion
adb shell am kill corsica.universita.journalvoyage           # mort du process (app en arrière-plan)
adb shell am crash corsica.universita.journalvoyage          # mort brutale (même pendant un trajet)
adb shell dumpsys deviceidle force-idle ; … ; unforce        # Doze
adb shell dumpsys activity services corsica.universita.journalvoyage   # le service : isForeground=true, types=0x8 (location)
adb emu geo fix 9.4500 42.7000                               # une position (émulateur : longitude d'abord)
adb shell pm get-app-links corsica.universita.journalvoyage  # verified
```
