# Domaći zadatak 1 – Istražite sopstvenu JVM

Sve sam radio na Mac-u (arm64), a JDK-ovi su instalirani preko Homebrew-a. Ispis svih komandi sam sačuvao u folderu `outputs/`, pa se svaki navedeni rezultat može proveriti.

## Šta se nalazi u folderu

| Fajl | Čemu služi |
|---|---|
| `ThreadLab.java` | Program koji smo dobili uz zadatak. Pokreće tri niti `worker-N` i sluša na portu (podrazumevano 9090). Nisam ga menjao. |
| `MemLab.java` | Moj pomoćni program za zadatak 04. Sa `heap` meri koliko nizova od 1 MB stane u heap, a sa `stack` koliko duboko ide rekurzija. |
| `EncodingLab.java` | Moj pomoćni program za zadatak 05. Upisuje tekst kao UTF-8, čita ga kao windows-1250 i ispisuje svaki pogrešan znak. |
| `outputs/` | Ispis komandi i eksperimenata, onako kako ga je terminal prikazao. |

Ako želite sve da pokrenete sami:

```
javac -encoding UTF-8 -d out ThreadLab.java MemLab.java EncodingLab.java
java -cp out ThreadLab
java -Xmx32m -cp out MemLab heap
java -Xss256k -cp out MemLab stack
java -cp out EncodingLab
```

---

## 01 Okruženje

Hteo sam da vidim koji JDK koriste komanda `java`, Maven i moji IDE-ovi (IntelliJ IDEA i VS Code). Ispis je u `outputs/01-environment.txt` i `outputs/01b-ide-and-jdk27.txt`.

| Alat | Koji JDK koristi | Zašto baš taj |
|---|---|---|
| `java` u terminalu | OpenJDK **27.0.1** (pre instalacije Mavena bio je 25.0.4.1) | `/usr/bin/java` je samo macOS stub. On pita `java_home`, a ovaj uzima **najnoviji** instalirani JDK. |
| `mvn -v` | OpenJDK **27.0.1** | Brew-ova skripta za `mvn` radi `JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk/...}"`. Znači, uzima `JAVA_HOME` ako postoji, a ako ne, Brew-ov najnoviji `openjdk`. |
| VS Code | sopstveni JRE **25.0.4.1** | Dolazi upakovan u Java ekstenziju (`redhat.java`). |
| IntelliJ IDEA 2025.2 | SDK podešen po projektu: `openjdk-21` i `openjdk@25` | Podešava se u projektu, `JAVA_HOME` ga ne zanima. |

`JAVA_HOME` kod mene nije postavljen. U `PATH`-u imam i `/opt/homebrew/opt/openjdk@21/bin`, ali JDK 21 uopšte nije instaliran, pa je ta putanja beskorisna. Isto važi za `openjdk-21` u IntelliJ-u, jer folder na koji pokazuje više ne postoji.

**Zašto se razlikuju:** svaki alat ima svoje pravilo za izbor JDK-a. Terminal ide preko `java_home`, Maven preko `JAVA_HOME` ili svoje podrazumevane vrednosti, a IDE-ovi koriste ono što im je ugrađeno ili podešeno u projektu. Ništa ih ne tera da se slože, pa se često i ne slažu.

**Jedna stvar na koju treba paziti:** pre `brew install maven` komanda `java` je pokazivala na JDK 25, a `mvn` nije ni postojao. Maven u Brew-u zavisi od formule `openjdk` (bez broja verzije), pa je instalacija usput povukla i JDK 27, a time se promenilo i šta radi `java`. Zadatke 02–05 sam prvo uradio na JDK 25, a memorijske testove sam posle ponovio na 27. Rezultati su isti.

---

## 02 Niti i port

Kompajlirao sam i pokrenuo `ThreadLab`, pa pomoću `jps`, `lsof`, `netstat` i `jcmd` našao PID, port i radne niti. Ispis je u `outputs/02-jps.txt` i `outputs/02-port-threads.txt`.

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

Šta se tu vidi:
- `jps` daje PID procesa, a to je 13251.
- `netstat` na macOS-u nema opciju `-p <pid>` kao na Linuxu, pa sam vezu između PID-a i porta našao preko `lsof`. Proces 13251 sluša na `*:9090`. Soket je IPv6, ali prima i IPv4 veze, zato u `netstat`-u piše `tcp46`.
- `jcmd ... Thread.print` ispisuje sve niti u JVM-u. Sve tri radne niti samo spavaju u `Thread.sleep(Long.MAX_VALUE)`, pa su u stanju `TIMED_WAITING (sleeping)`. Glavna nit čeka na `accept()`.

---

## 03 Zauzet port

Kad sam `ThreadLab` pokrenuo drugi put, dok je prvi još radio, dobio sam ovo (`outputs/03-port-in-use.txt`):

```
PID 13303
Exception in thread "main" java.net.BindException: Address already in use
    at java.base/sun.nio.ch.Net.bind0(Native Method)
    ...
    at ThreadLab.main(ThreadLab.java:8)
```

**Zašto:** prvi proces već drži `*:9090`. Operativni sistem dozvoljava samo jedan slušajući soket po adresi i portu, pa `bind()` ne uspe, a `new ServerSocket(port)` baci izuzetak.

**Neočekivano:** drugi proces se nije ugasio. `jps` ga je i dalje prikazivao, a `jcmd` je pokazao njegove tri niti `worker-`. Niti se pokreću pre otvaranja soketa i nisu daemon, pa JVM ostaje živ i kad `main` padne. Morao sam da ga ubijem ručno komandom `kill`.

**Rešenje 1, bez gašenja prvog procesa:** pokrenem program na drugom portu, jer ga `ThreadLab` prima kao argument.

```
$ java ThreadLab 9091
Slušam na portu 9091
```

`lsof` je posle toga pokazao oba procesa, 13251 na portu 9090 i 13351 na portu 9091.

**Rešenje 2, sa gašenjem prvog procesa:** nađem ko drži port, ugasim ga i pokrenem program ponovo na 9090.

```
$ lsof -ti :9090      ->  13251
$ kill 13251
$ java ThreadLab      ->  Slušam na portu 9090
```

---

## 04 Memorija

Za ovaj zadatak sam napisao `MemLab`. U režimu `heap` stavlja u listu nizove `new byte[1024 * 1024]` dok ne dobije `OutOfMemoryError`, pa ispiše koliko ih je stalo. U režimu `stack` rekurzivno poziva metodu dok ne dobije `StackOverflowError`, pa ispiše dubinu. Svaku konfiguraciju sam pokrenuo po 3 puta (`outputs/04-05-results.txt`). Garbage collector je podrazumevani G1, a region je veličine 1 MB u oba slučaja.

| Podešavanje | Rezultat (3 pokretanja) | Šta bismo očekivali |
|---|---|---|
| `-Xmx32m` | 15 nizova (15 / 15 / 15) | 32 |
| `-Xmx128m` | 63 niza (63 / 63 / 63) | 128 |
| `-Xss256k` | dubina 1479 (1479 / 1479 / 1479, na JDK 27: 1478) | – |
| `-Xss2m` | dubina oko 45 300–46 200 (45261 / 45664 / 46202) | – |

Za poređenje sam probao i Serial GC (`-XX:+UseSerialGC`): staje 28 nizova uz `-Xmx32m` i 118 uz `-Xmx128m`.

**Zašto heap daje duplo manje nego što očekujemo:** G1 deli heap na regione od 1 MB. Niz od 1 MB plus zaglavlje je malo veći od jednog regiona, pa ga G1 tretira kao *humongous* objekat i daje mu 2 uzastopna regiona. Zato staje otprilike `Xmx/2` nizova (32 regiona daju oko 16, 128 regiona oko 64), a jedan do dva regiona odu na ostale žive objekte. Serial GC nema humongous regione, pa se približava limitu (28 i 118). Ni on ne stigne do kraja, jer deo heap-a zauzimaju mlada generacija i survivor prostori, a veliki nizovi moraju da završe u staroj generaciji.

**Zašto dubina rekurzije nije proporcionalna steku:** stek je 8 puta veći, a dubina oko 30 puta veća. Deo steka JVM rezerviše za zaštitne zone, ali je važnije to što veličina okvira zavisi od načina izvršavanja koda. Na 256k prekoračenje nastaje dok se metoda još interpretira. Okviri su tada veliki (oko 170 B), pa je rezultat uvek isti. Na 2m JIT kompajler prevede `recurse()` dok rekurzija traje, kompajlirani okviri su mnogo manji (oko 45 B), pa ide dublje, i to malo drugačije u svakom pokretanju.

---

## 05 Encoding

Tekst `Čačak, Šabac, Žabalj, Ćuprija, Đerdap` upisao sam u `outputs/encoding.txt` kao UTF-8, a zatim ga pročitao kao windows-1250. U izvornom kodu slova su napisana kao `\u` sekvence, da sam izvorni fajl ne bi uticao na rezultat.

```
original: Čačak, Šabac, Žabalj, Ćuprija, Đerdap
as 1250 : ÄŚaÄŤak, Ĺ abac, Ĺ˝abalj, Ä†uprija, Ä�erdap
```

**Zašto se kvari:** u UTF-8 svako slovo van ASCII-ja zauzima 2 bajta. windows-1250 je jednobajtni encoding, pa svaki od ta 2 bajta pročita kao zaseban znak. Zato od jednog slova dobijamo dva pogrešna znaka. ASCII slova (`a`, `b`, `k`...) prolaze neoštećena jer imaju iste kodove u oba encodinga.

| Slovo | UTF-8 bajtovi | Šta windows-1250 vidi |
|---|---|---|
| Č | C4 8C | C4 je `Ä`, 8C je `Ś`, ispadne **ÄŚ** |
| č | C4 8D | C4 je `Ä`, 8D je `Ť`, ispadne **ÄŤ** |
| Š | C5 A0 | C5 je `Ĺ`, A0 je nedeljivi razmak, ispadne **Ĺ** i razmak |
| Ž | C5 BD | C5 je `Ĺ`, BD je `˝`, ispadne **Ĺ˝** |
| Ć | C4 86 | C4 je `Ä`, 86 je `†`, ispadne **Ä†** |
| Đ | C4 90 | C4 je `Ä`, a 90 u windows-1250 **nije definisan**, pa ispadne **Ä** i zamenski znak U+FFFD (**Ä�**) |

Prvi bajt određuje prvi znak: C4 uvek daje `Ä`, a C5 uvek `Ĺ`. Drugi bajt se traži u tabeli windows-1250, a na poziciji 0x90 u njoj nema ničega. Zato je `Đ` jedino slovo koje daje zamenski znak, i jedino kod koga se informacija zaista gubi, jer se original više ne može rekonstruisati. Ista vrsta greške vidi se i u samom tekstu zadatka (`Ĉaĉak`, `Ţabalj`), koji je očigledno prošao kroz pogrešnu konverziju encodinga.
