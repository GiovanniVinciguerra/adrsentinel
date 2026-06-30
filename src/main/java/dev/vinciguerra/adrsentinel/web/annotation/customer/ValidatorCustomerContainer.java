package dev.vinciguerra.adrsentinel.web.annotation.customer;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.customer.validator.CustomerContainerValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Annotazione custom per la validazione JSR-380 (Jakarta Bean Validation) del payload logistico.
 * <p>
 * <b>Scopo nel Dominio Architetturale:</b><br>
 * Questa annotazione agisce come punto di innesco per lo strato di Anti-Corruption. Garantisce che 
 * la configurazione degli attori coinvolti in una spedizione rispetti rigorosamente le regole di business.
 * Nello specifico, delega l'ispezione strutturale della collezione al {@link CustomerContainerValidator}, 
 * il quale verifica la presenza del modello logistico a "Triangolazione": esattamente un Mittente (SENDER), 
 * un Destinatario (RECEIVER) e un Vettore (CARRIER).
 * </p>
 * <p>
 * <b>Integrazione con Spring Boot:</b><br>
 * Progettata per essere applicata sui campi (FIELD) o sui parametri (PARAMETER) dei Data Transfer Object (DTO). 
 * Quando il framework intercetta questa annotazione (spinta da {@code @Valid}), invoca automaticamente 
 * il validatore associato, rigettando le richieste malformate prima che possano inquinare il Service Layer.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see CustomerContainerValidator
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { CustomerContainerValidator.class })
public @interface ValidatorCustomerContainer {
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
	String message() default "Malformed payload: invalid customer configuration.";
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
