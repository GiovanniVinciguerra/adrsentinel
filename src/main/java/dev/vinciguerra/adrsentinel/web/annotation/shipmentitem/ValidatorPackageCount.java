package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator.PackageCountValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom (Jakarta Bean Validation) dedicata al dominio ADR.
 * <p><b>Contesto Architetturale (Declarative Validation):</b></p>
 * Questa annotazione funge da marker dichiarativo per innescare la logica di business 
 * incapsulata nella classe {@link PackageCountValidator}. Applicabile a campi di classe (es. proprietà dei DTO) 
 * o parametri di metodo, permette di validare in modo trasparente e coeso il numero di colli 
 * al confine dell'applicazione, intercettando richieste malformate (HTTP 400) prima che 
 * raggiungano i Service o il Database.
 * <p><b>Regole di Dominio:</b></p>
 * Garantisce che il valore numerico inserito sia fisicamente coerente con le capacità 
 * di carico logistico del trasporto (impedendo valori fuori scala o negativi causati 
 * da errori umani di digitazione). Agisce come strato anti-corruzione (Anti-Corruption Layer) 
 * per la stabilità dei calcoli di stivaggio.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see PackageCountValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { PackageCountValidator.class })
public @interface ValidatorPackageCount {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: the required numeric value is missing or invalid (expected a strictly positive).";
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
