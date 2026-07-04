package dev.vinciguerra.adrsentinel.web.dto.customer;

import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCompanyName;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload in ingresso per la ricerca di un Cliente tramite Ragione Sociale.
 * <p><b>Ruolo Architetturale e Standardizzazione dell'Interfaccia:</b></p>
 * Questo record incapsula il parametro di ricerca all'interno di un oggetto strutturato, garantendo un design API 
 * uniforme e coeso (es. standardizzando gli endpoint di ricerca come richieste POST con Body, in perfetta simmetria 
 * con la ricerca per Partita IVA). Sebbene il nome di un'azienda sia tipicamente un dato di business pubblico, 
 * nel contesto logistico reale (es. ditte individuali, padroncini) la ragione sociale coincide spesso con il nome e cognome 
 * della persona fisica, assumendo a tutti gli effetti la valenza di Dato Personale (PII) soggetto a GDPR. 
 * Veicolare questo parametro tramite un DTO strutturato e protetto dal tunnel TLS consolida la "Pragmatic Security" del sistema.
 * <p><b>Frontiera di Sicurezza e Validazione (Fail-Fast):</b></p>
 * Operando come strato di barriera (Boundary Layer) all'ingresso del Controller, il record demanda la validazione attiva 
 * della stringa al constraint custom {@code @ValidatorCompanyName}. Se l'input contiene caratteri non ammessi o pattern 
 * potenzialmente pericolosi, la richiesta viene respinta istantaneamente (HTTP 400). Questo assicura che il Service Layer 
 * e le cache vengano interrogati esclusivamente con chiavi di ricerca sintatticamente pure, prevenendo sprechi di risorse 
 * e tentativi di iniezione.
 * @param companyName La Ragione Sociale esatta da ricercare. La conformità e la sicurezza del testo sono garantite dalla validazione JSR-380 custom.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Secure Search Payload)
 * @since 3.0
 */
public record CustomerSearchByNameRequestDTO(@ValidatorCompanyName String companyName) {}
