package dev.vinciguerra.adrsentinel.web.dto.customer;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorAddress;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorCompanyName;
import dev.vinciguerra.adrsentinel.web.annotation.customer.ValidatorVatNumber;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload in ingresso (Inbound Update Payload)
 * inviato dal client per la mutazione anagrafica di un'entità Cliente (Customer) preesistente.
 * <p><b>Ruolo Architetturale e Immutabilità della Business Key:</b></p>
 * Questo record è progettato specificamente per le operazioni di aggiornamento parziale o totale
 * (es. endpoint HTTP PUT o PATCH). A differenza del payload di creazione ({@link CustomerRequestDTO}),
 * <b>omette intenzionalmente</b> il campo relativo alla Partita IVA ({@code vatNumber}).
 * Questa è una precisa scelta di Domain-Driven Design (DDD): la Partita IVA rappresenta la
 * <i>Business Key</i> univoca e immutabile di un'entità legale. Qualsiasi variazione di tale
 * identificatore implicherebbe la nascita di un nuovo soggetto giuridico, non la mutazione
 * di quello esistente. Omettendola dal DTO, il sistema si blinda strutturalmente contro
 * tentativi di alterazione (Mass Assignment) su chiavi di dominio critiche.
 * <p><b>Frontiera di Sicurezza e Validazione (Fail-Fast):</b></p>
 * Operando come strato di barriera (Boundary Layer) all'ingresso del Controller, il record
 * applica una validazione attiva e preventiva sui dati in ingresso tramite specifiche
 * annotazioni JSR-380. Se la validazione fallisce, la richiesta viene respinta alla frontiera
 * (HTTP 400), garantendo che il Service Layer riceva esclusivamente mutazioni formalmente sicure.
 * @param vatNumber La partita iva del cliente di cui modificare i dettagli. Per motivi di sicurezza il vatNumber 
 * non è esposto come URL, ma integrato nel body della richiesta. Non viene mai modificato.
 * @param companyName La nuova ragione sociale o il nuovo nome commerciale da applicare al cliente. 
 * La validità formale della stringa è garantita dal constraint custom {@code @ValidatorCompanyName}.
 * @param legalAddress Il nuovo indirizzo della sede legale. La conformità strutturale della stringa è 
 * verificata dal constraint custom {@code @ValidatorAddress}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record CustomerUpdateDTO(@ValidatorVatNumber String vatNumber, @ValidatorCompanyName String companyName, @ValidatorAddress String legalAddress) {}
