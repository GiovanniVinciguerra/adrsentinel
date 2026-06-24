package dev.vinciguerra.adrsentinel.web.dto.driver;

import java.util.Set;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDate;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorDriverApprovals;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorFullName;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorLicense;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorPhoneNumber;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorTaxCode;

/**
 * Data Transfer Object (DTO) immutabile che modella il payload di richiesta (Request Payload) 
 * inviato dai client per la creazione o l'aggiornamento dei dati di un Conducente (Driver).
 * <p><b>Ruolo Architetturale:</b></p>
 * <p>Questo record funge da <i>Anti-Corruption Layer</i> a ridosso dei Controller REST. 
 * Garantisce che nessun dato malformato, incompleto o sintatticamente errato possa penetrare 
 * nei livelli di servizio o di dominio (Entities). Sfrutta l'immutabilità nativa dei {@code record} 
 * Java per garantire la thread-safety durante l'elaborazione concorrente delle richieste HTTP.</p>
 * <p><b>Strategia di Validazione (Fail-Fast):</b></p>
 * <p>Tutti i componenti di questo DTO sono stringhe (o collezioni di stringhe) protette da 
 * annotazioni di Bean Validation custom (es. {@code @ValidatorTaxCode}, {@code @ValidatorLocalDate}). 
 * Ricevere date ed enumerazioni come stringhe grezze al livello DTO permette ai validatori di 
 * intercettare errori di formato e restituire messaggi d'errore Minimalist REST personalizzati, 
 * evitando che il framework lanci eccezioni generiche e poco chiare (es. {@code HttpMessageNotReadableException}) 
 * durante la fase di deserializzazione JSON.</p>
 * @param fullName Il nome e cognome completo del conducente. Sottoposto a validazione tramite 
 * {@code @ValidatorFullName} per garantire l'assenza di caratteri speciali non ammessi 
 * e il rispetto delle lunghezze minime/massime.
 * @param taxCode Il numero di identificazione fiscale (es. Codice Fiscale, NIF, ecc.). 
 * Validato in ottica cross-border europea tramite {@code @ValidatorTaxCode}, 
 * che igienizza il dato e ne verifica la conformità tramite dizionario Regex.
 * @param phoneNumber Il recapito telefonico del conducente. Validato da {@code @ValidatorPhoneNumber} 
 * per assicurare la presenza di un prefisso internazionale valido (es. +39) 
 * e una struttura numerica coerente.
 * @param license Il numero della patente di guida. Verificato da {@code @ValidatorLicense} 
 * contro i pattern nazionali dei Paesi UE/Schengen/UK, previa sanitizzazione.
 * @param licenseExpireDate La data di scadenza della patente, ricevuta in formato stringa. 
 * L'annotazione {@code @ValidatorLocalDate} assicura che il dato rispetti 
 * rigorosamente lo standard ISO-8601 (es. YYYY-MM-DD) prima di qualsiasi 
 * tentativo di parsing nel layer di business.
 * @param cqcExpireDate La data di scadenza della Carta di Qualificazione del Conducente (CQC), 
 * qualora applicabile. Sottoposta alla medesima validazione strutturale 
 * tramite {@code @ValidatorLocalDate}.
 * @param driverApprovals Un set di stringhe che rappresentano le abilitazioni o certificazioni del conducente. 
 * L'annotazione {@code @ValidatorDriverApprovals} verifica che ogni elemento 
 * dell'array JSON corrisponda esattamente a una costante enumerata valida 
 * (es. {@code DriverApproval}), bloccando payload con certificazioni inesistenti.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record DriverRequestDTO(@ValidatorFullName String fullName, @ValidatorTaxCode String taxCode, @ValidatorPhoneNumber String phoneNumber, @ValidatorLicense String license,
	@ValidatorLocalDate String licenseExpireDate, @ValidatorLocalDate String cqcExpireDate, @ValidatorDriverApprovals Set<String> driverApprovals) {}
