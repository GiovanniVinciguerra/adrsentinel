package dev.vinciguerra.adrsentinel.web.dto.customer;

import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload in ingresso per la ricerca sicura di un Cliente.
 * <p><b>Evoluzione Architetturale e Protezione dei Dati PII (Security by Design):</b></p>
 * Questo record incarna una rigorosa scelta di sicurezza. Poiché la Partita IVA può rappresentare un Dato Personale
 * (PII) nel caso di ditte individuali o liberi professionisti, esporla direttamente nell'URI (come Path Variable
 * o Query Parameter) comporterebbe il grave rischio di tracciamento in chiaro nei file di log dei web server
 * (es. Nginx, Apache), dei proxy o dei sistemi APM. Incapsulando il parametro all'interno del Body di una richiesta
 * (tipicamente veicolata tramite un endpoint HTTP POST di ricerca), il dato sensibile viene protetto integralmente
 * dalla cifratura in transito (TLS/HTTPS) e mantenuto opaco ai log di infrastruttura.
 * <p><b>Frontiera di Sicurezza (Fail-Fast):</b></p>
 * Agendo come barriera al livello del Controller, il DTO garantisce che la query di ricerca raggiunga
 * il Service Layer e il Database solo se l'identificatore fornito supera un rigoroso controllo di conformità strutturale,
 * prevenendo attacchi di iniezione e alleggerendo il motore relazionale da elaborazioni a vuoto.
 * @param vatNumber La Partita IVA (Business Key) da ricercare. (N.B. Si consiglia di sostituire l'attuale constraint 
 * @ValidatorLicense con @ValidatorVatNumber per garantire la validazione dell'identificativo fiscale).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Secure Search Payload)
 * @since 3.0
 */
public record CustomerSearchByVatRequestDTO(@ValidatorLicense String vatNumber) {}
