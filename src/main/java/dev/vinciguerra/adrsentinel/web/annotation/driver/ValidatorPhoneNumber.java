package dev.vinciguerra.adrsentinel.web.annotation.driver;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.driver.validator.PhoneNumberValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di validazione custom per verificare la correttezza formale e tipologica 
 * di un numero di telefono cellulare.
 * <p>
 * Questa annotazione garantisce che la stringa fornita in input rispetti i seguenti criteri:
 * <ul>
 * <li>Non sia nulla, vuota o composta da soli spazi (blank).</li>
 * <li>Non superi la lunghezza massima consentita per prevenire attacchi di tipo DoS 
 * sui cicli di parsing della CPU (limite massimo prefissato nel validatore).</li>
 * <li>Sia parsabile secondo lo standard internazionale (es. E.164) o un formato locale valido.</li>
 * <li>Appartenga specificamente a un'utenza <b>Mobile</b> (o <b>Fixed-Line/Mobile</b>), 
 * scartando di conseguenza i numeri di pura rete fissa.</li>
 * </ul>
 * </p>
 * <p><b>Integrazione Architetturale:</b></p>
 * La validazione effettiva è delegata alla classe {@link PhoneNumberValidator}, 
 * la quale sfrutta librerie di terze parti (es. <code>libphonenumber</code> di Google) 
 * per l'analisi e la risoluzione del numero.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see PhoneNumberValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { PhoneNumberValidator.class })
public @interface ValidatorPhoneNumber {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: invalid mobile phone number format";
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
