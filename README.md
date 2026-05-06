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
1. Essere eccessivamente prudenti (facendo viaggiare mezzi mezzi vuoti e perdendo marginalità).
2. Rischiare per ottimizzare i costi, esponendosi a sanzioni.

**AdrSentinel nasce per risolvere definitivamente questo conflitto.**

---

## La Soluzione: Ottimizzazione & Sicurezza
AdrSentinel non è un semplice gestionale dati, ma un vero e proprio **motore decisionale logistico**. Le sue funzionalità core sono progettate per massimizzare il profitto aziendale, annullando matematicamente il rischio di infrazioni:

* **Load Optimization (Cost Reduction):** Un algoritmo che assiste nella scelta del mezzo di trasporto perfetto. Ottimizza lo spazio e il peso delle merci, garantendo al contempo che sostanze chimicamente incompatibili (es. infiammabili ed esplosivi) non vengano mai caricate sullo stesso veicolo, rispettando i limiti normativi (es. regola dei 1000 punti).
* **Gestione e Tracciabilità Immutabile:** Uno storico completo, cachato e ultra-performante di ogni spedizione, veicolo e singola riga di carico. Essenziale per la reportistica e per superare brillantemente gli audit normativi aziendali.

---

## Architettura del Dominio: La Legge diventa Codice
Il cuore pulsante di AdrSentinel è il suo Database, progettato secondo i principi del **Domain-Driven Design (DDD)**. Le complesse leggi del trasporto europeo non sono state affrontate con semplici controlli "If/Else" nel codice, ma sono state modellate intrinsecamente nella struttura relazionale dei dati su **PostgreSQL**.

* **Il Dominio Normativo:** Le entità `un_numbers` (sostanze chimiche) e `adr_classes` sono collegate da una matrice di compatibilità (`compatibility_rules`). Il database stesso "sa" se la Classe A può viaggiare con la Classe B, bloccando alla radice inserimenti illegali.
* **Il Dominio Logistico:** L'entità `shipment` funge da anello di congiunzione tra le regole chimiche (`shipment_item`) e i vincoli fisici del camion (`vehicle`).

---

## Qualità del Codice e Pattern Architetturali
Questo backend è stato sviluppato ponendo un'attenzione maniacale alla qualità, alle performance e alla manutenibilità a lungo termine:

* **Enterprise Documentation:** Nessuna classe è lasciata al caso. L'intero codice sorgente è documentato con **Javadoc dettagliatissimi** che spiegano non solo il *cosa*, ma il *perché* (Design Pattern utilizzati, scelte architetturali, flussi di transazione).
* **Pattern Applicati:** Fail-Fast Validation (Custom Annotations), Surrogate Business Keys (UUID) per blindare l'integrità dei dati in memoria e prevenire *Ghost Records*.
* **Caching Ibrido (Write-Through):** Implementazione di strategie avanzate di manipolazione diretta della cache (Caffeine) per abbattere i tempi di risposta senza incappare in dati obsoleti, mantenendo la coerenza transazionale.
* **JPA/Hibernate Tuning:** Prevenzione proattiva di N+1 queries e `LazyInitializationException` tramite un uso consapevole del *Dirty Checking* e del *Read-Only Defaulting*.

---

## Roadmap & Next Steps (Lavori in Corso)
AdrSentinel è un progetto vivo e in continua evoluzione. I prossimi moduli attualmente in fase di sviluppo/progettazione includono:

1. **Dynamic Safe-Routing (GIS Integration):** L'integrazione di un motore GIS basato su Java (come **GraphHopper**) per calcolare percorsi "ad hoc". Il sistema incrocerà le dimensioni fisiche del veicolo e la classe ADR trasportata con i dati stradali (OpenStreetMap), deviando automaticamente il percorso per evitare ponti vietati, strade troppo strette o tunnel preclusi a specifiche sostanze.
2. **Test-Driven Reliability:** Sebbene la logica sia attualmente blindata da vincoli SQL e validazioni Spring, il prossimo passo prevede una copertura totale del Service Layer tramite **JUnit e Mockito**, per garantire zero regressioni durante l'aggiunta di nuove norme ADR.

---

## Il Valore per l'Azienda (Business Impact)
L'obiettivo di questa architettura software non è solo "eseguire istruzioni", ma generare un tangibile vantaggio competitivo:
- **Riduzione drastica dell'errore umano** nella pianificazione dei carichi ADR.
- **Risparmio economico (Carburante/Pedaggi)** grazie all'ottimizzazione del riempimento dei mezzi.
- **Zero sanzioni** e totale sicurezza legale per gli autisti e il management.