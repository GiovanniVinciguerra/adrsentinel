package dev.vinciguerra.adrsentinel.web.dto.customer;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorAddress;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCompanyName;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorVatNumber;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload in ingresso (Inbound Request Payload)
 * inviato dal client per la creazione o la mutazione di un'entità Cliente (Customer).
 * <p><b>Ruolo Architetturale e Frontiera di Sicurezza:</b></p>
 * Questo record funge da strato di barriera (Boundary Layer) all'ingresso del sistema (tipicamente a livello 
 * del Controller REST). Il suo scopo primario è disaccoppiare i dati crudi forniti dall'esterno 
 * dall'entità di dominio interna, prevenendo attacchi di tipo "Over-Posting" o "Mass Assignment". 
 * L'utilizzo del costrutto nativo {@code record} garantisce la totale immutabilità, rendendo l'oggetto 
 * intrinsecamente thread-safe durante l'intero ciclo di elaborazione della richiesta.
 * <p><b>Strategia di Validazione "Fail-Fast" (JSR-380):</b></p>
 * Questo DTO si comporta come un proxy di validazione attiva. Ogni singolo parametro è presidiato 
 * da annotazioni custom basate sulle specifiche Jakarta Bean Validation (es. Hibernate Validator). 
 * Se il payload in ingresso risulta malformato, il framework intercetta le violazioni alla frontiera 
 * (sollevando tipicamente una {@code MethodArgumentNotValidException}) e blocca l'elaborazione restituendo 
 * immediatamente un HTTP 400 (Bad Request). Questo paradigma assicura che il Service Layer e il 
 * Database operino in uno stato di "Trust", ricevendo esclusivamente dati sintatticamente e formalmente puri.
 * @param companyName La ragione sociale o il nome commerciale del cliente. La validità formale della stringa 
 * (es. gestione degli apici, lunghezza ammessa, assenza di pattern malevoli) è delegata 
 * al constraint di validazione custom {@code @ValidatorCompanyName}.
 * @param vatNumber Il numero di identificazione fiscale (Partita IVA / VAT Number). L'integrità del formato 
 * (es. prefisso nazionale, lunghezza a 11 cifre per standard IT, check-digit) è 
 * rigorosamente validata e garantita dal constraint custom {@code @ValidatorVatNumber}.
 * @param legalAddress L'indirizzo completo della sede legale. La conformità strutturale della stringa, 
 * necessaria per i successivi calcoli di routing o di fatturazione, è verificata 
 * tramite il constraint custom {@code @ValidatorAddress}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record CustomerRequestDTO(@ValidatorCompanyName String companyName, @ValidatorVatNumber String vatNumber, @ValidatorAddress String legalAddress) {}
