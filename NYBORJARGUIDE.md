# Nybörjarguide: så funkar GitHub och Stilla

Den här guiden är för dig, Jan. Den förklarar med enkla ord vad vi gör när vi
bygger Stilla: vad ett repo är, vad en merge är, vad alla lösenord var till och
vad som händer när en ny version åker till Google Play.

Du behöver inte kunna allt på en gång. Läs en del i taget. Samma ord kommer
tillbaka flera gånger med flit, så att de sätter sig.

Guiden uppdateras när något ändras i hur du jobbar med appen.

---

## 1. Orden, ett i taget

### Repo

Ett **repo** är en mapp på nätet där all kod för Stilla ligger, plus hela
historiken: varje ändring som någonsin gjorts.

- Stillas repo: https://github.com/palsk1/Stilla-Launcher
- Tänk på det som mappen **Stilla launcher** på ditt skrivbord, fast på GitHub,
  och med ett minne av alla gamla versioner.

### Commit

En **commit** är en sparad ändring med en kort förklaring. Som när du sparar
ett dokument, men varje sparning får ett namn och blir kvar för alltid.

- Exempel: den allra första commiten i Stilla heter "Stilla 0.2.0". Den
  innehåller hela appen som den såg ut när den lades upp.
- Om något blir fel kan man alltid gå tillbaka till en gammal commit.

### Branch (gren)

En **branch** är en egen kopia av koden där man kan ändra saker utan att röra
huvudversionen.

- Huvudversionen heter **master**. Det som ligger på master är "den riktiga"
  Stilla.
- När Claude ändrar något görs det på en egen branch, till exempel
  `claude/nyborjarguide-...`. Där kan det vara hur ofärdigt som helst utan att
  något går sönder.

### Push

**Push** betyder "skicka upp mina commits till GitHub". Innan en push finns
ändringen bara på den dator där den gjordes.

- Claude pushar till sin egen branch, aldrig direkt till master.

### Pull request (PR)

En **pull request** är en fråga: "Får de här ändringarna läggas in i master?"
Där kan du se exakt vad som ändrats, rad för rad, innan du säger ja.

- Stillas PR:ar hittills:
  - **#1** Uppdaterad README (öppen, väntar)
  - **#2** Ta bort gamla filer (öppen, väntar)
  - **#3** Automatisk uppladdning till Play (klar, ligger i master)
- Du hittar dem under fliken **Pull requests** högst upp i repot.

### Draft (utkast)

En **draft PR** är en pull request som är märkt "inte klar än". Den kan inte
slås ihop av misstag. När den är färdig trycker man **Ready for review**.

### Merge (slå ihop)

**Merge** betyder att ändringarna från en branch läggs in i master. Efter en
merge är ändringen en del av den riktiga Stilla.

- **Viktigt:** när kod läggs in i master skickas en ny version av appen till
  Google Play automatiskt (se del 3). Därför frågar Claude dig alltid innan en
  merge.
- Ändringar som bara rör textfiler som den här guiden (filer som slutar på
  `.md`) skickar **inget** till Play.

### Pull (hämta)

**Pull** är motsatsen till push: "hämta det nya från GitHub till min dator".
Det behövs om du har en kopia av repot på datorn (se del 5).

---

## 2. Så går en ändring från idé till din telefon

1. **Du ber Claude** om något, till exempel "gör texten större".
2. Claude gör en **branch** och sparar ändringen som en eller flera **commits**.
3. Claude **pushar** branchen till GitHub.
4. Claude öppnar en **draft PR** och skickar länken till dig.
5. Du tittar (om du vill) och säger "ja, slå ihop".
6. Claude (eller du) gör en **merge** till master.
7. **GitHub Actions** (en robot hos GitHub) märker att master ändrats. Den
   kör testerna, bygger appen och skickar den till **intern testning** på
   Google Play.
8. Efter en stund kan din telefon uppdatera Stilla från Play Butik.

Kort sagt: **branch → commit → push → PR → merge → Play → telefonen.**

---

## 3. Automatisk uppladdning till Google Play

Det här satte vi upp den 6 oktober 2026 (PR #3).

**Vad händer?** Varje gång något nytt (förutom bara `.md`-textfiler) hamnar i
master startar en robot hos GitHub. Den:

1. Hämtar koden.
2. Kör Stillas 52 tester (så att inget viktigt har gått sönder).
3. Bygger appen och skriver under den med din nyckel.
4. Skickar den till **intern testning** på Google Play. Det är bara du som ser
   den, ingen annan.

Versionsnumret räknas ut automatiskt (förra versionen + 1), så du behöver
aldrig ändra det själv.

### Se om det gick bra

1. Öppna https://github.com/palsk1/Stilla-Launcher/actions
2. Överst ser du den senaste körningen.
   - **Gul prick** = jobbar fortfarande.
   - **Grön bock** = klart, versionen ligger på Play.
   - **Rött kryss** = något gick fel. Säg till Claude, så tittar Claude på
     det. Inget skickades till Play då.

### Texten "Nyheter" i Play

Det som står under "Nyheter" på Play kommer från filen
`app/src/main/play/release-notes/sv-SE/internal.txt`. Just nu står det
"Första testversionen via Google Play." Vill du ha en annan text, säg bara
vad det ska stå så lägger Claude in det före nästa uppladdning.

### Starta en uppladdning för hand

Om du vill skicka en ny version utan att något ändrats:

1. Gå till https://github.com/palsk1/Stilla-Launcher/actions
2. Klicka på **Play internal testing** i listan till vänster.
3. Klicka på knappen **Run workflow** till höger, och sedan på den gröna
   **Run workflow**.

---

## 4. Alla lösenorden: vad var de till?

Det kändes som väldigt många lösenord, så här är det enkelt:

**Varför behövdes de?** Google Play tar bara emot en app om den är
**underskriven** med din nyckel, och om den som skickar har **lov** att
skicka. Förut fanns båda sakerna bara på din dator. Nu fick GitHubs robot en
egen kopia, så att den kan göra jobbet åt dig.

**Var ligger de?** I ett låst fack på GitHub som heter **Secrets**
(hemligheter):
https://github.com/palsk1/Stilla-Launcher/settings/secrets/actions

Där kan ingen läsa dem, inte du, inte Claude, inte någon annan. Roboten får
bara använda dem medan den bygger. Man kan byta ut en hemlighet, men aldrig
se den igen.

**De fem hemligheterna:**

| Namn | Vad det är | Kom från |
|---|---|---|
| `STILLA_UPLOAD_KEYSTORE_BASE64` | Själva nyckeln som skriver under appen (gjord om till text) | filen `upload-key.jks` |
| `STILLA_KEYSTORE_PASSWORD` | Lösenordet till nyckelfilen | raden `storePassword` i `keystore.properties` |
| `STILLA_KEY_ALIAS` | Nyckelns namn (inget hemligt egentligen) | raden `keyAlias` |
| `STILLA_KEY_PASSWORD` | Lösenordet till själva nyckeln | raden `keyPassword` |
| `PLAY_SERVICE_ACCOUNT_JSON` | "Passerkortet" som ger lov att skicka till Play | filen `play-service-account.json` |

Tänk så här: de fyra första är **nyckeln och dess lösenord** (för att skriva
under appen). Den femte är **passerkortet** till Google Play (för att få
lämna in den).

**Viktigt att komma ihåg:**

- Originalfilerna ligger fortfarande bara i mappen **Stilla launcher** på din
  dator. Ha en säkerhetskopia av dem någon annanstans också (till exempel ett
  USB-minne). Tappar du `upload-key.jks` kan Google hjälpa dig byta nyckel,
  men det är krångligt.
- Ge aldrig de här filerna eller lösenorden till någon, inte heller till
  Claude i chatten. Claude behöver dem aldrig.
- Filerna laddas aldrig upp i repot. Repot är inställt på att ignorera dem
  (filen `.gitignore` säger det).
- Passerkortet får bara skicka till **testspår** på Play, inte till den
  riktiga butiken. Så även i värsta fall kan det inte nå vanliga användare.

---

## 5. Din kopia på datorn

Mappen **Stilla launcher** på skrivbordet är din egen kopia. Den uppdateras
**inte** av sig själv när något läggs in i master på GitHub.

- Om du bygger med Android Studio på datorn, hämta först det nya från GitHub
  (en **pull**). I programmet **GitHub Desktop**: klicka på
  **Fetch origin** högst upp, och om knappen sedan byter namn till
  **Pull origin**, klicka på den.
- Om du någon gång **pushar** till master från datorn startar det också en
  uppladdning till Play, precis som en merge.
- Du kan fortfarande skicka till Play från Android Studio som förut
  (`publishReleaseBundle`). Båda sätten fungerar, men du behöver inte längre
  göra det själv.

---

## 6. Så gör du en merge själv (om du vill)

Oftast gör Claude det åt dig när du säger ja. Men så här går det till:

1. Öppna PR:en (länken Claude skickat, eller fliken **Pull requests**).
2. Är den ett utkast (grå text "Draft") klicka först **Ready for review**
   längst ner.
3. Scrolla ner till den gröna knappen **Merge pull request** (kan också heta
   **Squash and merge**) och klicka.
4. Klicka **Confirm merge**.
5. Klart. Om ändringen var mer än bara text startar nu uppladdningen till Play
   (del 3).

Ångrar du dig efteråt: säg till Claude. Allt finns kvar i historiken och kan
backas.

---

## 7. Kom ihåg (den korta versionen)

- **Repo** = Stillas mapp på GitHub, med hela historiken.
- **Commit** = en sparad ändring med ett namn.
- **Branch** = en egen arbetskopia. **master** är den riktiga.
- **Push** = skicka upp till GitHub. **Pull** = hämta ner.
- **PR** = "får det här läggas in i master?". **Draft** = inte klar än.
- **Merge** = lägg in i master. Då åker appen till Play automatiskt.
- **Secrets** = det låsta facket med nyckeln och passerkortet. Ingen kan
  läsa dem.
- Grön bock under **Actions** = ny version ligger på Play.
