package dev.vinciguerra.adrsentinel.web.dto.driver;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDate;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorFullName;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorPhoneNumber;

/**
 * Data Transfer Object (DTO) immutabile utilizzato per incapsulare il payload 
 * di richiesta durante le operazioni di aggiornamento (es. PUT o PATCH) dei dati 
 * anagrafici e documentali di un autista (Driver).
 * <p>
 * Essendo implementato come <code>record</code>, questa struttura dati è 
 * intrinsecamente immutabile e thread-safe, garantendo che le informazioni 
 * trasferite dal layer esposto (API/Controller) al layer applicativo (Service) 
 * non subiscano alterazioni intermedie.
 * </p>
 * <p><b>Strategia di Validazione:</b></p>
 * Il DTO adotta un approccio di validazione dichiarativa basata su costrutti 
 * custom di Jakarta Bean Validation. Qualora il payload in ingresso non rispetti 
 * i criteri imposti dalle annotazioni, la richiesta viene bloccata (Fail-Fast) 
 * dal framework (restituendo tipicamente un HTTP 400 Bad Request) prima ancora 
 * di impegnare la logica di business.
 * @param fullName Il nome e cognome completo dell'autista. 
 * Validato tramite {@link ValidatorFullName} per garantirne la correttezza 
 * formale (es. limiti di lunghezza, assenza di caratteri speciali non ammessi).
 * @param phoneNumber Il contatto telefonico mobile dell'autista. 
 * Validato tramite {@link ValidatorPhoneNumber} per assicurarne il rispetto 
 * degli standard E.164 e l'appartenenza a una rete mobile.
 * @param licenseExpireDate La data di scadenza della patente di guida. 
 * Validata tramite {@link ValidatorLocalDate} per verificarne la validità 
 * formale e il corretto parsing (es. formato ISO-8601).
 * @param cqcExpireDate La data di scadenza della CQC (Carta di Qualificazione del Conducente), 
 * titolo abilitativo obbligatorio per il trasporto professionale di merci o persone. 
 * Validata tramite {@link ValidatorLocalDate}.
 */
public record DriverUpdateDTO(@ValidatorFullName String fullName, @ValidatorPhoneNumber String phoneNumber,
	@ValidatorLocalDate String licenseExpireDate, @ValidatorLocalDate String cqcExpireDate) {}
