# AdrSentinel (Server-Side)
> **Logistica, Ottimizzazione dei Costi e Sicurezza Normativa per il Trasporto di Merci Pericolose (ADR)**

### Tech Stack
![Java](https://img.shields.io/badge/Java-21-red?style=for-the-badge&logo=java)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18.3-316192?style=for-the-badge&logo=postgresql&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-Domain_Driven_Design-orange?style=for-the-badge)

### Spring Ecosystem & ORM
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Web](https://img.shields.io/badge/Spring_Web-REST_API-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-Persistence-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Hibernate](https://img.shields.io/badge/Hibernate-ORM_Engine-59666C?style=for-the-badge&logo=Hibernate&logoColor=white)

## Il Problema Reale (Perché nasce AdrSentinel?)
Nel mondo della logistica, il trasporto di Merci Pericolose (sottoposto alla rigida normativa europea ADR) è un vero e proprio campo minato. Un singolo errore di carico, o la scelta di un percorso sbagliato (es. attraversare una galleria non autorizzata), può portare a **sanzioni penali, disastri ambientali e perdite economiche devastanti**.

Le aziende di trasporto si trovano quotidianamente davanti a un bivio: 
1. Essere eccessivamente prudenti (facendo viaggiare veicoli mezzi vuoti e perdendo marginalità).
2. Rischiare per ottimizzare i costi, esponendosi a sanzioni.

**AdrSentinel nasce per risolvere definitivamente questo conflitto.**

AdrSentinel non si premura solo delle aziende, ma anche dell'autista di mezzi pesanti:
1. La scelta del percorso migliore e del percorso consentito per il trasporto può essere un incubo, soprattutto per chi ha poca esperienza.

---

## La Soluzione: Ottimizzazione & Sicurezza
AdrSentinel non è un semplice gestionale dati, ma un vero e proprio **motore decisionale logistico**. Le sue funzionalità core sono progettate per massimizzare il profitto aziendale, annullando matematicamente il rischio di infrazioni:

* **Load Optimization (Cost Reduction):** Un algoritmo che assiste nella scelta del mezzo di trasporto perfetto. Ottimizza lo spazio e il peso delle merci, garantendo al contempo che sostanze chimicamente incompatibili (es. infiammabili ed esplosivi) non vengano mai caricate sullo stesso veicolo, rispettando i limiti normativi (es. regola dei 1000 punti).
* **Gestione e Tracciabilità Immutabile:** Uno storico completo, cachato e ultra-performante di ogni spedizione, veicolo e singola riga di carico. Essenziale per la reportistica e per superare brillantemente gli audit normativi aziendali.
* **Scelta del Percorso Migliore:** Un algoritmo che cuce il percorso sulla tipologia di merce trasportata e sul veicolo che la trasporta, tenendo anche conto dei tempi di riposo, per legge, dell'autista di mezzi pesanti.

---

## Architettura del Dominio: La Legge diventa Codice
Il cuore pulsante di AdrSentinel è il suo Database, progettato secondo i principi del **Domain-Driven Design (DDD)**. Le complesse leggi del trasporto italiano non sono state affrontate con semplici controlli "If/Else" nel codice, ma sono state modellate intrinsecamente nella struttura relazionale dei dati su **PostgreSQL**.

* **Il Dominio Normativo:** Le entità `onu_numbers` (sostanze chimiche) e `adr_classes` sono collegate da una matrice di compatibilità (`compatibility_rules`). Il database stesso "sa" se la Classe A può viaggiare con la Classe B, bloccando alla radice inserimenti illegali.
* **Il Dominio Logistico:** L'entità `shipment` funge da anello di congiunzione tra le regole chimiche (`shipment_item`) e i vincoli fisici del del veicolo (`vehicle`), dell'autista (`driver`) e dei clienti (Mittente, Destinatario e Vettore) che partecipano alla spedizione (`customer`).

---

## Qualità del Codice e Pattern Architetturali
Questo backend è stato sviluppato ponendo un'attenzione maniacale alla qualità, alle performance e alla manutenibilità a lungo termine:

* **Enterprise Documentation:** Nessuna classe è lasciata al caso. L'intero codice sorgente è documentato con **Javadoc dettagliatissimi** che spiegano non solo il *cosa*, ma il *perché* (Design Pattern utilizzati, scelte architetturali).
* **Pattern Applicati:** Fail-Fast Validation (Custom Annotations), Surrogate Business Keys (UUID) per blindare l'integrità dei dati in memoria e prevenire *Ghost Records*.
* **Caching Ibrido (Write-Through):** Implementazione di strategie avanzate di manipolazione diretta della cache (Caffeine) per abbattere i tempi di risposta senza incappare in dati obsoleti, mantenendo la coerenza transazionale.
* **Gestione della sicurezza (Input Validation e SQL Injection)**: Implementazione dei comuni pattern di sicurezza per evitare problemi di **SQL Injection** (PreparedStatement) e **Parameter Pollution** (fail-on-reading-dup-tree-key); inoltre tutte le richieste vengono filtrate da un **Firewall di livello Application** che valida costantemente l'input proveniente dal client, questo per evitare di distruggere la solidità relazionale del DataBase.

---

## Roadmap & Next Steps (Lavori in Corso)
AdrSentinel è un progetto vivo e in continua evoluzione. I prossimi moduli attualmente in fase di sviluppo/progettazione includono:

- [x] **Load Optimization:** Implementazione di un algoritmo per l'assegnazione automatica del veicolo e dell'autista ottimali in base alla spedizione ADR. Il sistema ottimizza l'impiego della flotta aziendale selezionando il veicolo disponibile con la portata utile minore, pur garantendo la capienza sufficiente per il carico. Oltre a questo criterio di efficienza dimensionale, l'algoritmo verifica la compatibilità delle certificazioni del mezzo, valuta l'applicabilità dell'esenzione parziale (regola dei 1000 punti) e si assicura contestualmente che l'autista sia in possesso del patentino CFP ADR in corso di validità per le specifiche classi di pericolo trasportate.
- [x] **Dynamic Safe-Routing (HeiGIT Integration):** Implementazione di un client per la comunicazione REST con le API GIS di HeiGIT. Questa integrazione permette di esternalizzare il calcolo topologico dei percorsi sicuri: inviando al servizio il payload con ingombro del mezzo e classe ADR, il sistema ottiene in tempo reale il tracciato ottimizzato su mappa, garantendo il pieno rispetto dei divieti di transito per il trasporto di merci pericolose.
- [ ] **Bolla di Viaggio:** Creazione automatica della bolla di viaggio in formato PDF da scaricare. La bolla di viaggio una volta creata viene memorizzata per sempre nel database pronta per essere fornita immediatamente per un nuovo download.
- [ ] **Test-Driven Reliability:** Sebbene la logica sia attualmente blindata da vincoli SQL e validazioni Spring, il prossimo passo prevede una copertura totale del Service Layer tramite **JUnit e Mockito**, per garantire zero regressioni durante l'aggiunta di nuove norme ADR.

---

## Il Valore per l'Azienda (Business Impact)
L'obiettivo di questa architettura software non è solo "eseguire istruzioni", ma generare un tangibile vantaggio competitivo:
- **Riduzione drastica dell'errore umano** nella pianificazione dei carichi ADR.
- **Risparmio economico (Carburante/Pedaggi)** grazie all'ottimizzazione del riempimento dei mezzi.
- **Zero sanzioni** e totale sicurezza legale per gli autisti e il management.
- **Zero stress** per gli autisti di mezzi pesanti.