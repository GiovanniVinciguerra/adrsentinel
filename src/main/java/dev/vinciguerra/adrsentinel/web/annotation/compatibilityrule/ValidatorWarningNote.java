package dev.vinciguerra.adrsentinel.web.annotation.compatibilityrule;

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
 * Annotazione di validazione custom (Constraint Composition) per la verifica formale 
 * della nota operativa (Warning Note) all'interno delle regole di compatibilità ADR.
 * <p>
 * <b>Obiettivo Architetturale (DRY & Ubiquitous Language):</b><br>
 * Questa interfaccia funge da meta-annotazione (wrapper) per i vincoli standard di 
 * Jakarta/Hibernate Validator. Centralizza la regola di business relativa alla lunghezza 
 * massima ammessa per le note operative. Invece di disperdere l'annotazione 
 * {@code @Size(max = 255)} su molteplici DTO, Entità o firme di metodi, l'utilizzo di 
 * {@code @ValidatorWarningNote} garantisce che la regola risieda in un'unica fonte di verità 
 * (Single Source of Truth). Qualora le specifiche di database o normative cambiassero, 
 * l'adeguamento avverrà esclusivamente in questa classe.
 * </p>
 * <p>
 * <b>Dettagli Implementativi:</b>
 * <ul>
 * <li><b>Limite Fisico ({@link Size}):</b> Impone un limite rigido di 255 caratteri, 
 * perfettamente allineato con la lunghezza standard di una colonna {@code VARCHAR} su database.</li>
 * <li><b>Delega di Validazione:</b> L'attributo {@code validatedBy = {}} all'interno di 
 * {@code @Constraint} è intenzionalmente vuoto. Comunica al motore di validazione di Spring 
 * che non esiste una classe validatrice custom in Java (nessun {@code ConstraintValidator} 
 * da istanziare), ma che il motore deve unicamente valutare la somma delle meta-annotazioni 
 * applicate all'interfaccia (in questo caso, solo {@code @Size}).</li>
 * <li><b>Contesti di Applicazione (Targeting):</b> È configurata per operare sia sui campi 
 * delle classi ({@code FIELD}, tipico dei Request DTO) sia sui parametri espliciti dei 
 * metodi ({@code PARAMETER}, utile nei Controller REST per validazioni isolate).</li>
 * </ul>
 * </p>
 * @return Il messaggio di errore restituito al frontend in caso di fallimento della validazione.
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
	message = "Compatibility rule warning note must not exceed 255 characters."
)
public @interface ValidatorWarningNote {
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
	String message() default "Compatibility rule warning note must not exceed 255 characters.";
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
