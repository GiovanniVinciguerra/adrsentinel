package dev.vinciguerra.adrsentinel.web.annotation.dispatch;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.dispatch.validator.NetWeightValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione custom (Fail-Fast) per garantire l'integrità matematica e logistica
 * dei pesi netti dichiarati per le merci pericolose.
 * <p>
 * Nel dominio dei trasporti, il peso netto non è un semplice dato informativo, ma una variabile 
 * matematica critica utilizzata dal motore decisionale. È fondamentale, ad esempio, per il calcolo 
 * del moltiplicatore normativo necessario a stabilire l'applicabilità dell'esenzione parziale 
 * (Regola dei 1000 punti - ADR cap. 1.1.3.6).
 * </p>
 * <p>
 * Questa annotazione assicura che il valore target sia una grandezza fisica valida, verificando che sia:
 * <ul>
 * <li>Strettamente maggiore di zero (i pesi negativi o nulli sono privi di senso logistico).</li>
 * <li>Un numero reale e finito (prevenendo anomalie computazionali come {@code NaN} o {@code Infinity} 
 * che genererebbero eccezioni fatali nei calcoli a valle).</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 * @see NetWeightValidator
 */ 
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { NetWeightValidator.class })
public @interface ValidatorNetWeight {
	/**
	 * Definisce il messaggio di errore predefinito che verrà restituito al client 
	 * (es. esposto tramite {@code MethodArgumentNotValidException} nel GlobalExceptionHandler) 
	 * qualora la stringa superi il limite massimo consentito.
	 * <p>
	 * Sebbene sia cablato (hardcoded) in inglese per garantire un comportamento di default 
	 * autonomo, questo valore può essere agevolmente scavalcato a runtime:
	 * <ul>
	 * <li>Passando una stringa custom: {@code @ValidatorWarningNote(message = "Errore...")}</li>
	 * <li>Sfruttando l'interpolazione i18n di Spring tramite file properties 
	 * (es. {@code {validation.warningNote.size.message}}).</li>
	 * </ul>
	 * </p>
	 * @return il messaggio testuale che descrive la violazione del vincolo.
	 */
	String message() default "Malformed payload: value must be a number > 0.";
	/**
	 * Permette di partizionare l'esecuzione di questa validazione raggruppandola logicamente 
	 * (Validation Groups).
	 * <p>
	 * <b>Caso d'uso Enterprise:</b> Consente di attivare o disattivare il vincolo in base 
	 * al contesto transazionale. Ad esempio, passando interfacce marcatore (Marker Interfaces) 
	 * si potrebbe esigere questa validazione solo in fase di "Conferma Ordine" 
	 * (es. {@code @Validated(OnConfirm.class)}) e ignorarla in fase di "Salvataggio Bozza" 
	 * (es. {@code @Validated(OnDraft.class)}).
	 * </p>
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo (vuoto di default, 
	 * il che significa che appartiene al gruppo predefinito {@code Default.class}).
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente ai client dell'API di allegare metadati o oggetti informativi (Payload) 
	 * specifici alla violazione di questo vincolo.
	 * <p>
	 * <b>Caso d'uso Enterprise:</b> Viene tipicamente sfruttato per indicare il <b>livello 
	 * di severità</b> dell'errore (es. creando payload come {@code Severity.Info.class} o 
	 * {@code Severity.Error.class}). Un gestore centralizzato delle eccezioni (ControllerAdvice) 
	 * può leggere questo payload e decidere se bloccare la richiesta con un 400 Bad Request 
	 * oppure lasciarla passare restituendo un semplice alert non bloccante al frontend.
	 * </p>
	 * @return l'array delle classi payload associate alla validazione (vuoto di default).
	 */
	Class<? extends Payload>[] payload() default {};
}
