package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator.PackageTypeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Jakarta Bean Validation) dedicata al dominio ADR.
 * <p><b>Contesto Architetturale (Anti-Corruption Layer):</b></p>
 * Questa annotazione funge da marker dichiarativo per innescare la logica di business 
 * incapsulata nella classe {@link PackageTypeValidator}. Il suo scopo primario è agire 
 * come barriera protettiva al confine dell'applicazione (strato di trasporto / DTO). 
 * Intercetta input testuali malformati prima che raggiungano i servizi di dominio, 
 * prevenendo crash infrastrutturali e fatali eccezioni di parsing (es. le classiche 
 * {@code IllegalArgumentException} lanciate dal metodo {@code Enum.valueOf()}).
 * <p><b>Regole di Dominio (Integrità del Dizionario Logistico):</b></p>
 * Garantisce che il tipo di imballaggio fornito (es. DRUM, IBC, TANK) appartenga 
 * strettamente al dizionario normativo chiuso supportato dal sistema. Qualsiasi valore 
 * non riconosciuto, nullo o vuoto viene respinto alla radice, assicurando che le 
 * successive logiche di validazione incrociata (es. controllo pesi e colli) 
 * e la stesura del D.D.T. si basino su anagrafiche certe e normate.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see PackageTypeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { PackageTypeValidator.class })
public @interface ValidatorPackageType {
	/**
	 * Definisce il messaggio di errore predefinito restituito al client (REST Payload) 
	 * qualora il tipo di imballaggio risulti assente o non coincida con i valori ammessi.
	 * <p><i>Nota di Sicurezza:</i> Il messaggio è formulato in ottica "Minimalist REST" 
	 * per comunicare chiaramente il difetto sintattico al chiamante (HTTP 400 Bad Request), 
	 * mascherando intenzionalmente l'implementazione interna e i nomi delle enumerazioni Java.</p>
	 * @return la stringa contenente il messaggio di errore standardizzato.
	 */
	String message() default "Malformed payload: the required package type is missing or unrecognized.";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici gruppi di validazione 
	 * (Validation Groups).
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (di default vuoto).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente all'architettura di sistema di allegare metadati informativi (Payload) 
	 * alla violazione del vincolo, utili per tracciare livelli di severità dell'errore (es. WARNING, FATAL).
	 * @return l'array delle classi di payload estese (di default vuoto).
	 */
	Class<? extends Payload>[] payload() default {};
}
