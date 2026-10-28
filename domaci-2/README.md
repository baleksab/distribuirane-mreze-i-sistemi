# Domaći zadatak 2 – Mrežni detektiv

Sve sam radio na Mac-u (arm64) sa OpenJDK 27.0.1. Ispis svih komandi sam sačuvao u folderu `outputs/`, pa se svaki navedeni rezultat može proveriti.

## Šta se nalazi u folderu

| Fajl | Čemu služi |
|---|---|
| `ThreadLab.java` | Program iz prvog domaćeg, isti je kao tamo. Sluša na portu 9090. Nisam ga menjao. |
| `HttpProba.java` | Program iz zadatka. Šalje GET na zadati URL i ispisuje status i zaglavlja, ili ime izuzetka i poruku. Nisam ga menjao. |
| `self-signed.badssl.com.pem` | Sertifikat koji sam skinuo za zadatak 05. |
| `moj-truststore.jks` | Moj truststore (lozinka `changeit`) sa tim jednim sertifikatom. Zapravo je PKCS12, iako ima `.jks` nastavak. |
| `outputs/` | Ispis komandi, onako kako ga je terminal prikazao. |

Ako želite sve da pokrenete sami:

```
javac -encoding UTF-8 -d out ThreadLab.java HttpProba.java
java -cp out ThreadLab &
java -cp out HttpProba http://localhost:9090
java -cp out HttpProba http://localhost:9091
java -cp out HttpProba http://10.255.255.1
java -cp out HttpProba https://example.com
java -cp out HttpProba https://expired.badssl.com
java -cp out HttpProba https://self-signed.badssl.com
java -Djavax.net.ssl.trustStore=moj-truststore.jks -Djavax.net.ssl.trustStorePassword=changeit -cp out HttpProba https://self-signed.badssl.com
```

---

## 01 DNS

Koristio sam `nslookup` i `dig` (`outputs/01-dns.txt`, `outputs/01b-internal.txt`).

| Ime | Rezultat |
|---|---|
| `example.com` | IPv4: **104.20.23.154** i **172.66.147.243**, IPv6: `2606:4700:10::6814:179a` i `2606:4700:10::ac42:93f3` |
| `localhost` | **127.0.0.1** (i `::1` za IPv6) |
| `intranet.firma.internal` | **NXDOMAIN**, tj. ime ne postoji |

Pošto nemam pristup stvarnoj mreži nekog preduzeća, za "interni server" sam izabrao ime `intranet.firma.internal`. To je tipično ime koje bi neko preduzeće dalo svom internom serveru, a samo ime je izmišljeno. Zato ni IP adresu nemam kako da napišem. Da sam u toj mreži, njen DNS server bi vratio privatnu adresu iz opsega `10.x.x.x`, `172.16–31.x.x` ili `192.168.x.x`.

**Zašto se interno ime ne razrešava van mreže preduzeća:** zapis za `intranet.firma.internal` postoji samo na DNS serveru preduzeća, koji je autoritativan za tu zonu. Moj resolver ne zna za tu zonu, pa pita root servere. Oni ne znaju ništa o domenu `.internal`, pa vrate NXDOMAIN, što se vidi u `dig` ispisu (`status: NXDOMAIN`, a u AUTHORITY sekciji je SOA zapis root zone). Osim toga, i da zapis negde postoji, vodio bi na privatnu adresu, a privatne adrese nisu rutabilne na internetu. Zato se interna imena i adrese razrešavaju samo kad je računar na toj mreži (ili preko VPN-a, koji mu podmetne DNS server preduzeća).

**Usput:** prvo sam probao `intranet.firma.local`, ali je `nslookup` samo pisao `connection timed out`. Razlog je što macOS imena sa nastavkom `.local` šalje preko mDNS-a (u `scutil --dns` se vidi `domain: local, options: mdns`), a ne na DNS server. Zato sam prešao na `.internal`.

Kod `localhost` odgovor ne dolazi s interneta. Ime je u `/etc/hosts`, a `nslookup` je ipak vratio 127.0.0.1 jer ga resolver provajdera sam odgovara. `dscacheutil` (koji prolazi kroz sistemski resolver) takođe vraća `127.0.0.1` i `::1`.

---

## 02 Četiri ishoda

Pokrenuo sam `ThreadLab` (sluša na 9090) i `HttpProba` pozvao na tri adrese iz zadatka (`outputs/02-cetiri-ishoda.txt`). Naslov kaže "četiri ishoda", pa sam kao četvrti dodao uspešan poziv na `https://example.com` (`outputs/02b-uspeh.txt`).

| Poziv | Izuzetak | Trajanje |
|---|---|---|
| `http://localhost:9090` | `java.io.IOException`: *HTTP/1.1 header parser received no bytes* | 35 ms |
| `http://localhost:9091` | `java.net.ConnectException`: *Connection refused* | 38 ms |
| `http://10.255.255.1` | `java.net.http.HttpConnectTimeoutException`: *HTTP connect timed out* | 3030 ms |
| `https://example.com` | nema izuzetka, status **200** | 188 ms |

**9090, veza uspe, ali nema odgovora:** TCP veza se uspostavi, jer `ThreadLab` stvarno sluša na tom portu. Međutim, `ThreadLab` u petlji radi `server.accept().close()`, dakle prihvati vezu i odmah je zatvori, ne pročitavši zahtev i ne poslavši ništa. Klijent je poslao GET i čeka status liniju, a umesto nje dobije kraj veze. Zato parser kaže da nije primio nijedan bajt. Ovo je greška aplikacionog sloja, a ne mreže.

**9091, odbijena veza:** na tom portu niko ne sluša. Kernel na `localhost`-u odmah odgovori RST paketom i klijent odmah dobije `ConnectException`. Brzo je (38 ms) jer odgovor stiže odmah, ne čeka se ništa.

**10.255.255.1, timeout:** ova adresa je privatna i kod mene ne postoji nijedan uređaj koji bi je imao. SYN paket ode, ali odgovor nikad ne stigne, ni SYN-ACK ni RST, niti ga je iko odbio. Klijent čeka dok ne istekne `connectTimeout` od 3 sekunde iz `HttpProba`, što se i vidi: 3030 ms. Razlika u odnosu na 9091 je važna: **odbijena veza znači da je mašina tu, ali port nije otvoren, a timeout znači da ne znamo ništa**, jer je paket nestao (nema rute, firewall ga je tiho odbacio, ili mašine nema).

**example.com, uspeh:** DNS, TCP, TLS i HTTP su prošli, server je vratio 200 i zaglavlja.

---

## 03 HTTP

Komanda: `curl -v https://example.com` (`outputs/03-curl-v.txt`).

| Šta | Vrednost | Gde u ispisu |
|---|---|---|
| IP adresa | **104.20.23.154** (curl je dobio i 172.66.147.243, ali je prvu probao i ostao na njoj) | `Connected to example.com (104.20.23.154) port 443` |
| Verzija TLS-a | **TLS 1.3** (šifra `AEAD-CHACHA20-POLY1305-SHA256`) | `SSL connection using TLSv1.3` |
| Izdavač sertifikata | **Cloudflare TLS Issuing ECC CA 3** (O=SSL Corporation, C=US) | `issuer: C=US; O=SSL Corporation; CN=Cloudflare TLS Issuing ECC CA 3` |
| Statusni kod | **200** (protokol HTTP/2) | `< HTTP/2 200` |

Tri zaglavlja odgovora:
- `content-type: text/html; charset=utf-8` kaže da je telo HTML u UTF-8 encodingu (baš ono o čemu je bilo u prvom domaćem).
- `server: cloudflare` kaže da odgovara Cloudflare, a ne neki server na samoj adresi example.com. Uz to idu i `cf-cache-status: HIT` i `age: 12367`, tj. odgovor je poslat iz keša, star oko 3,4 sata.
- `allow: GET, HEAD` kaže koje HTTP metode resurs podržava.

Sertifikat je važeći od 26. septembra do 25. decembra 2026, a curl javlja `SSL certificate verify ok`.

---

## 04 Sertifikati

`HttpProba` na dva sajta, bez ikakvog isključivanja provere (`outputs/04-sertifikati.txt`). Oba puta je `javax.net.ssl.SSLHandshakeException`, ali sa različitom porukom:

| Sajt | Poruka |
|---|---|
| `expired.badssl.com` | `(certificate_expired) PKIX path validation failed: ... validity check failed` |
| `self-signed.badssl.com` | `(certificate_unknown) PKIX path building failed: ... unable to find valid certification path to requested target` |

Datumi i izdavači sertifikata su u `outputs/04b-expired-sertifikat.txt` i `outputs/05a-sertifikat.txt`.

**Razlika:**
- **Istekao sertifikat:** lanac je ceo i poznat. Sertifikat je izdao COMODO CA, kome Java veruje, ali mu je važnost bila od 9. do 12. aprila **2015**, a sad je 2026. Java je uspela da sagradi putanju do pouzdanog korena (*path building* je prošao), ali je pala **provera važnosti** (*path validation*, `validity check failed`). Sertifikat je legitiman, samo prestao da važi. Rešava se tako što vlasnik sajta obnovi sertifikat, a klijent tu ne može ništa.
- **Samopotpisan sertifikat:** sertifikat je vremenski ispravan (važi do oktobra 2028), ali je `issuer` isti kao `subject`, tj. sam sebe je potpisao. Java nije mogla ni da **sagradi putanju** do nekog korena kome veruje (*path building*, `unable to find valid certification path`), jer taj izdavač nije u njenom truststore-u. Ne znamo da li je to stvarno taj sajt. Rešava se tako što klijent odluči da veruje baš tom sertifikatu (zadatak 05).

Kratko: kod prvog je problem **vreme**, a kod drugog **poverenje**. Ne isključujem proveru ni u jednom slučaju, jer bi tada svako mogao da se predstavi kao bilo koji sajt.

---

## 05 Truststore

Koraci (`outputs/05-truststore.txt`):

```
# 1. skidanje sertifikata koji server šalje
openssl s_client -connect self-signed.badssl.com:443 -servername self-signed.badssl.com </dev/null | openssl x509 > self-signed.badssl.com.pem

# 2. uvoz u sopstveni truststore
keytool -importcert -alias self-signed-badssl -file self-signed.badssl.com.pem \
        -keystore moj-truststore.jks -storepass changeit -noprompt

# 3. pokretanje sa tim truststore-om
java -Djavax.net.ssl.trustStore=moj-truststore.jks \
     -Djavax.net.ssl.trustStorePassword=changeit \
     -cp out HttpProba https://self-signed.badssl.com
```

Ispis: `Status: 200` za 744 ms. Poziv sad uspeva, a provera nije isključena. Samo sam rekao Javi da baš tom sertifikatu veruje (SHA-256 otisak `BB:A2:1B:74:...:AC:A7`).

**Dve stvari na koje treba paziti:**
- Sopstveni truststore **zamenjuje** podrazumevani (`cacerts`), ne dodaje se na njega. Zato sa `moj-truststore.jks` Java ne veruje ni jednom drugom sajtu. To sam proverio: `expired.badssl.com` i dalje pada, sad sa `certificate_unknown`, jer u truststore-u nema ni Comodo korena. Ako bi mi trebali i obični sajtovi, napravio bih kopiju `cacerts` i u nju uvezao sertifikat.
- Pre uvoza treba proveriti otisak sertifikata nekim drugim putem. Ako bih slepo poverovao sertifikatu koji mi je neko podmetnuo, samo bih ručno uradio ono što Java inače sprečava. Ovde je u pitanju badssl.com, test sajt, pa je to u redu.
