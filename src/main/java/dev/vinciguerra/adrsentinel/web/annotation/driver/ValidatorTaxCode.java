package dev.vinciguerra.adrsentinel.web.annotation.driver;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.driver.validator.TaxCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di vincolo personalizzata (Custom Constraint) per la validazione formale 
 * dei Codici Fiscali o Numeri di Identificazione Fiscale (TIN - Tax Identification Number) europei.
 * <p>Questa annotazione delega la logica di controllo algoritmica alla classe {@link TaxCodeValidator}.</p>
 * <p><b>Casi d'uso e logiche applicate dal validatore:</b></p>
 * <ul>
 * <li><b>Validazione sintattica cross-border:</b> Controllo dei TIN inviati dai client, supportando la diversità 
 * dei formati degli Stati Membri UE e di alcuni Paesi extra-UE.</li>
 * <li><b>Igienizzazione automatica (Sanitization):</b> Rimozione silente di spazi, virgole, punti, trattini e 
 * underscore, seguita da conversione in maiuscolo.</li>
 * <li><b>Verifica di conformità Regex:</b> Match del valore ripulito contro un dizionario di pattern nazionali, con 
 * controllo preventivo sui limiti di lunghezza strutturale (da 8 a 16 caratteri).</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see TaxCodeValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { TaxCodeValidator.class })
public @interface ValidatorTaxCode {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: invalid tax identification number format.";
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
