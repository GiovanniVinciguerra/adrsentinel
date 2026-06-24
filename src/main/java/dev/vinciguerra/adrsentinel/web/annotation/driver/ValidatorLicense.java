package dev.vinciguerra.adrsentinel.web.annotation.driver;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.driver.validator.LicenseValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione di vincolo personalizzata (Custom Constraint) utilizzata per la validazione 
 * di licenze o dizionari di dati all'interno del ciclo di vita dell'applicazione.
 * <p>Questa annotazione si integra con l'ecosistema Jakarta Validation (o Hibernate Validator)
 * e delega la logica di controllo algoritmica alla classe {@code LicenseValidator}.</p>
 * <p><b>Casi d'uso tipici:</b></p>
 * <ul>
 * <li><b>Validazione sintattica su stringhe:</b> Controllo dei numeri di patente inviati dal client 
 * (il validatore supporta esclusivamente il tipo {@link String}).</li>
 * <li><b>Igienizzazione automatica dell'input (Sanitization):</b> Rimozione silente di spaziature, virgole, 
 * punti, trattini, slash e underscore prima della validazione, con conversione implicita in maiuscolo.</li>
 * <li><b>Verifica di conformità Regex:</b> Match del valore ripulito contro un dizionario di pattern nazionali 
 * specifici (es. formato DVLA per GB, 10 cifre per Belgio/Paesi Bassi, ecc.) e controllo sui limiti di lunghezza 
 * strutturale (da 5 a 20 caratteri).</li>
 * </ul>
 * <p>L'annotazione è configurata per essere mantenuta a runtime, consentendo l'ispezione 
 * tramite Reflection da parte dei framework di validazione.</p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see LicenseValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { LicenseValidator.class })
public @interface ValidatorLicense {
	/**
	 * Definisce il messaggio di errore unificato restituito al client (REST Payload) 
	 * qualora la collezione contenga almeno un elemento vuoto, nullo o non riconosciuto a dizionario.
	 * @return la stringa contenente il messaggio in standard Minimalist REST (es. HTTP 400).
	 */
	String message() default "Malformed payload: invalid driving license format.";
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
