package dev.vinciguerra.adrsentinel.web.dto.driver;

import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;

/**
 * Data Transfer Object (DTO) immutabile che incapsula i parametri di ricerca per l'interrogazione 
 * sicura delle risorse di tipo Conducente (Driver) all'interno del sistema.
 * <p><b>Contesto Architetturale e Sicurezza (POST for Search):</b></p>
 * <p>Questo record è stato progettato specificamente per essere mappato all'interno del 
 * {@code @RequestBody} di un endpoint HTTP POST. Questa strategia architetturale evita l'esposizione 
 * di Dati Personali Identificabili (PII) sensibili, come il numero di patente, all'interno degli URL. 
 * Viaggiando nel corpo della richiesta cifrato da TLS/HTTPS, il dato viene protetto da intercettazioni 
 * e non viene tracciato in chiaro nei log dei web server o nelle cronologie di navigazione.</p>
 * <p><b>Strategia di Validazione (Fail-Fast):</b></p>
 * <p>Il DTO delega il controllo formale dell'input al motore di Bean Validation. L'uso di annotazioni 
 * custom previene attacchi di tipo Injection e blocca le richieste malformate con un errore HTTP 400 
 * (Bad Request) prima che raggiungano i layer di servizio o scatenino query a vuoto sul database.</p>
 * @param license Il numero di patente di guida che agisce come chiave di ricerca primaria. 
 * Il campo è protetto dall'annotazione {@code @ValidatorLicense}, che ne garantisce 
 * l'igienizzazione automatica (rimozione di spazi/caratteri speciali) e la conformità 
 * strutturale rispetto ai pattern dei Paesi europei prima dell'elaborazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Secure Search Payload)
 * @since 3.0
 */
public record DriverSearchRequestDTO(@ValidatorLicense String license) {}
