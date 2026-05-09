package dev.vinciguerra.adrsentinel.web.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Size;

/**
 * Vincolo di validazione perimetrale centralizzato (Macro-Vincolo) per campi testuali 
 * opzionali (Not Required) a lunghezza standard (massimo 255 caratteri).
 * <p><b>Contesto Architetturale (Gestione Dati Opzionali & DRY):</b></p>
 * Questa meta-annotazione funge da gemella speculare della validazione per i campi obbligatori. 
 * È progettata per consolidare la validazione di tutti quei campi ausiliari nel dominio 
 * (es. note operative, descrizioni aggiuntive, dettagli secondari) in un'unica 
 * "Fonte di Verità". Applica rigorosamente il principio DRY (Don't Repeat Yourself), 
 * ripulendo i Data Transfer Object (DTO) e standardizzando la gestione dei testi opzionali.
 * <p><b>Motore di Validazione (Comportamento Condizionale):</b></p>
 * Sfruttando la composizione dei vincoli ({@code @Constraint(validatedBy = {})}), 
 * questo validatore opera in modo permissivo sull'assenza del dato, ma restrittivo sulla sua forma:
 * <ul>
 * <li><b>Tolleranza ai Null (Assenza di {@code @NotNull}):</b> Secondo le specifiche di 
 * Jakarta Validation, se un campo è {@code null}, l'annotazione {@code @Size} viene 
 * bypassata considerando il dato valido. Se il client non invia il campo, l'API 
 * non genererà alcun errore 400 (Bad Request).</li>
 * <li><b>Allineamento al Database ({@code @Size}):</b> Se il client decide di valorizzare 
 * il campo (stringa non nulla), scatta il limite architetturale di 255 caratteri. 
 * Questo protegge l'infrastruttura SQL da attacchi di <i>Payload Bloating</i> e previene 
 * le fatali eccezioni di <i>Data Truncation</i> (Overflow) durante il salvataggio 
 * su colonne {@code VARCHAR(255)}.</li>
 * </ul>
 * <p><b>Applicabilità:</b></p>
 * Progettata per essere applicata su campi testuali ({@code String}) all'interno dei DTO 
 * o sui parametri non obbligatori dei Controller REST.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@Size(
	max = 255,
	message = "String must not exceed 255 characters."
)
public @interface ValidatorNotRequiredString {
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
	String message() default "String must not exceed 255 characters.";
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
