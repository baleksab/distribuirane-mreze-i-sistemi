# Domaći zadatak 1 – Istražite sopstvenu JVM

Okruženje: macOS (arm64), JDK-ovi instalirani preko Homebrew-a. Sirovi izlazi komandi nalaze se u folderu `outputs/`.

## Sadržaj foldera

| Fajl | Čemu služi |
|---|---|
| `ThreadLab.java` | Zadati program: pokreće tri niti `worker-N` i sluša na portu (podrazumevano 9090). Nije menjan. |
| `MemLab.java` | Pomoćni program za zadatak 04: meri koliko nizova od 1 MB staje u heap (`heap`) i koliko duboka rekurzija staje na stek (`stack`). |
| `EncodingLab.java` | Pomoćni program za zadatak 05: upisuje tekst kao UTF-8, čita ga kao windows-1250 i ispisuje svaki pogrešan znak. |
| `outputs/` | Sirovi izlazi svih komandi i eksperimenata. |
| `out/` | Prevedene `.class` datoteke (nisu potrebne za predaju). |

Pokretanje:

```
javac -encoding UTF-8 -d out ThreadLab.java MemLab.java EncodingLab.java
java -cp out ThreadLab
java -Xmx32m -cp out MemLab heap
java -Xss256k -cp out MemLab stack
java -cp out EncodingLab
```

---

## 01 Okruženje

**Šta je urađeno:** upoređeno je koji JDK koriste komanda `java`, Maven (`mvn -v`) i IDE-ovi (IntelliJ IDEA i VS Code). Izlazi su u `outputs/01-environment.txt` i `outputs/01b-ide-and-jdk27.txt`.

| Alat | JDK koji koristi | Kako se bira |
|---|---|---|
| `java` (terminal) | OpenJDK **27.0.1** (pre instalacije Mavena bio je 25.0.4.1) | `/usr/bin/java` je samo macOS stub koji pita `java_home`, a ovaj bira **najnoviji** instalirani JDK |
| `mvn -v` | OpenJDK **27.0.1** (`/opt/homebrew/Cellar/openjdk/27.0.1.0.3/...`) | Brew skripta za `mvn`: `JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/...}"`, tj. koristi `JAVA_HOME` ako je postavljen, a inače Brew-ov najnoviji `openjdk` |
| VS Code (Java ekstenzija) | sopstveni JRE **25.0.4.1** | ugrađen u ekstenziju `redhat.java` |
| IntelliJ IDEA 2025.2 | SDK podešen po projektu u `jdk.table.xml`: `openjdk-21` (folder više ne postoji) i `openjdk@25` | podešavanje projekta, ne `JAVA_HOME` |

`JAVA_HOME` nije postavljen. U `PATH`-u postoji i putanja `/opt/homebrew/opt/openjdk@21/bin`, ali JDK 21 nije instaliran pa ona nema nikakvog efekta.

**Zašto se razlikuju:** svaki alat ima svoje pravilo za izbor JDK-a. Terminalski `java` koristi `java_home` (najnoviji), Maven koristi `JAVA_HOME` ili svoj Brew podrazumevani JDK, a IDE-ovi koriste ugrađeni JRE ili SDK podešen po projektu. Ništa ih ne primorava da se poklope.

**Važna napomena:** pre `brew install maven` komanda `java` je pokazivala na JDK 25, a `mvn` nije postojao. Maven zavisi od formule `openjdk` (bez broja verzije), pa je instalacija povukla i JDK 27, što je tiho promenilo rezultat komande `java`. Zadaci 02–05 prvi put su izvršeni na JDK 25, a memorijski testovi su ponovljeni na JDK 27 sa istim rezultatima.

---

## 02 Niti i port

**Šta je urađeno:** `ThreadLab` je preveden i pokrenut, a zatim su `jps`, `lsof`, `netstat` i `jcmd` upotrebljeni da se pronađu PID, port i radne niti. Izlazi su u `outputs/02-jps.txt` i `outputs/02-port-threads.txt`.

```
$ jps -l
13251 ThreadLab

$ lsof -nP -iTCP -sTCP:LISTEN -a -p 13251
COMMAND   PID USER   FD   TYPE   DEVICE SIZE/OFF NODE NAME
java    13251 c425    5u  IPv6   0xe11c...    0t0  TCP *:9090 (LISTEN)

$ netstat -anp tcp | grep 9090
tcp46      0      0  *.9090                 *.*                    LISTEN

$ jcmd 13251 Thread.print | grep -A2 worker-
"worker-1" #25 ... java.lang.Thread.State: TIMED_WAITING (sleeping)
"worker-2" #26 ... java.lang.Thread.State: TIMED_WAITING (sleeping)
"worker-3" #27 ... java.lang.Thread.State: TIMED_WAITING (sleeping)
```

**Objašnjenje:**
- `jps` daje PID Java procesa (13251).
- `netstat` na macOS-u nema opciju `-p <pid>`, pa se za vezu PID–port koristi `lsof`. Ona pokazuje da proces 13251 sluša na `*:9090` (IPv6 soket prihvata i IPv4, otuda `tcp46`).
- `jcmd ... Thread.print` ispisuje sve niti JVM-a. Tri radne niti stoje u `Thread.sleep(Long.MAX_VALUE)`, pa su u stanju `TIMED_WAITING (sleeping)`. Glavna nit je blokirana u `accept()`.

---

## 03 Zauzet port

**Šta je urađeno:** `ThreadLab` je pokrenut drugi put dok prvi još radi, a zatim je greška rešena na dva načina. Izlaz je u `outputs/03-port-in-use.txt`.

```
PID 13303
Exception in thread "main" java.net.BindException: Address already in use
    at java.base/sun.nio.ch.Net.bind0(Native Method)
    ...
    at ThreadLab.main(ThreadLab.java:8)
```

**Uzrok:** prvi proces već drži `*:9090` u stanju LISTEN. Operativni sistem dozvoljava samo jedan slušajući soket po adresi i portu, pa `bind()` ne uspeva i `new ServerSocket(port)` baca izuzetak.

**Zanimljivo zapažanje:** drugi proces se nije ugasio. `jps` ga je i dalje prikazivao, a `jcmd` je pokazao njegove 3 niti `worker-`. Niti se pokreću pre otvaranja soketa i nisu daemon niti, pa JVM ostaje živ iako je `main` pao. Proces je morao ručno da se ubije (`kill`).

**Rešenje 1, bez gašenja prvog procesa:** drugi port, jer program prihvata port kao argument.

```
$ java ThreadLab 9091
Slušam na portu 9091
```

`lsof` je prikazao oba procesa: 9090 (13251) i 9091 (13351).

**Rešenje 2, sa gašenjem prvog procesa:** pronađe se proces koji drži port, ugasi se, pa se program pokrene ponovo na 9090.

```
$ lsof -ti :9090      ->  13251
$ kill 13251
$ java ThreadLab      ->  Slušam na portu 9090
```

---

## 04 Memorija

**Šta je urađeno:** `MemLab heap` u listu stavlja nizove `new byte[1024 * 1024]` dok ne dobije `OutOfMemoryError` i ispisuje koliko ih je stalo. `MemLab stack` rekurzivno poziva metodu dok ne dobije `StackOverflowError` i ispisuje dubinu. Svaka konfiguracija pokrenuta je 3 puta. Izlaz je u `outputs/04-05-results.txt`. Podrazumevani GC je G1, a veličina regiona je 1 MB za oba heap-a.

| Podešavanje | Rezultat (3 pokretanja) | Teorijski maksimum |
|---|---|---|
| `-Xmx32m` | 15 nizova (15 / 15 / 15) | 32 |
| `-Xmx128m` | 63 niza (63 / 63 / 63) | 128 |
| `-Xss256k` | dubina 1479 (1479 / 1479 / 1479; na JDK 27: 1478) | – |
| `-Xss2m` | dubina oko 45 300–46 200 (45261 / 45664 / 46202) | – |

Dodatna provera: sa `-XX:+UseSerialGC` staje 28 nizova uz `-Xmx32m` i 118 uz `-Xmx128m`.

**Odstupanje kod heap-a:** staje otprilike duplo manje nizova nego što bi se očekivalo. G1 deli heap na regione od 1 MB. Niz od 1 MB plus zaglavlje veći je od polovine regiona, pa se tretira kao *humongous* objekat i zauzima 2 uzastopna regiona. Zato staje oko `Xmx/2` nizova (32 regiona daju oko 16, 128 regiona oko 64), a još jedan region ili dva odu na ostale žive objekte. Serial GC nema humongous regione pa se približava granici (28 i 118). Ostatak se gubi na mladu generaciju i survivor prostore, jer veliki nizovi moraju da žive u staroj generaciji.

**Odstupanje kod steka:** dubina nije proporcionalna veličini steka (8 puta veći stek daje oko 30 puta veću dubinu). Deo steka je rezervisan za zaštitne zone. Mnogo važnije je da veličina okvira zavisi od načina izvršavanja. Kod 256k do prekoraka dolazi dok se metoda još *interpretira* (veliki okviri, oko 170 B, pa je broj determinističan). Kod 2m JIT kompajler prevede `recurse()` usred rekurzije, kompajlirani okviri su znatno manji (oko 45 B), pa je dubina veća i malo varira od pokretanja do pokretanja.

---

## 05 Encoding

**Šta je urađeno:** tekst `Čačak, Šabac, Žabalj, Ćuprija, Đerdap` upisan je u `outputs/encoding.txt` kao UTF-8, a zatim pročitan kao windows-1250. U izvornom kodu slova su pisana kao `\u` escape sekvence da sam izvorni fajl ne bi bio dodatna promenljiva. Izlaz je u `outputs/04-05-results.txt`.

```
original: Čačak, Šabac, Žabalj, Ćuprija, Đerdap
as 1250 : ÄŚaÄŤak, Ĺ abac, Ĺ˝abalj, Ä†uprija, Ä�erdap
```

**Zašto dolazi do greške:** svako slovo van ASCII-ja u UTF-8 zauzima 2 bajta. windows-1250 je jednobajtni encoding, pa se svaki bajt pretvara u poseban znak. Zato se jedno ispravno slovo pretvara u dva pogrešna znaka. ASCII slova (`a`, `b`, `k`, ...) ostaju ista jer su im kodovi isti u oba encodinga.

| Original | UTF-8 bajtovi | Kako ih windows-1250 čita |
|---|---|---|
| Č | C4 8C | C4 = `Ä`, 8C = `Ś` → **ÄŚ** |
| č | C4 8D | C4 = `Ä`, 8D = `Ť` → **ÄŤ** |
| Š | C5 A0 | C5 = `Ĺ`, A0 = nerazdvojivi razmak → **Ĺ** + razmak |
| Ž | C5 BD | C5 = `Ĺ`, BD = `˝` → **Ĺ˝** |
| Ć | C4 86 | C4 = `Ä`, 86 = `†` → **Ä†** |
| Đ | C4 90 | C4 = `Ä`, 90 **nije definisan** u windows-1250 → **Ä** + zamenski znak U+FFFD (**Ä�**) |

Prvi bajt (C4 ili C5) određuje prvi znak: C4 daje `Ä`, a C5 daje `Ĺ`. Drugi bajt se čita iz tabele windows-1250, a bajt 0x90 u njoj nema dodeljen znak. Zato je `Đ` jedino slovo koje daje zamenski znak, tj. gubi informaciju. Isti efekat vidi se i u samom tekstu zadatka (`Ĉaĉak`, `Ţabalj`), koji je oštećen pogrešnom konverzijom encodinga.
