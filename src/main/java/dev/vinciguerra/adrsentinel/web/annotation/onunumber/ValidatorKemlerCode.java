package dev.vinciguerra.adrsentinel.web.annotation.onunumber;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator.KemlerCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Vincolo di validazione perimetrale (Edge Validation) per il Codice Kemler (Hazard Identification Number).
 * <p><b>Contesto di Dominio (Normativa ADR):</b></p>
 * Il codice Kemler è il numero identificativo del pericolo, posizionato nella metà 
 * superiore dei pannelli arancioni sui mezzi di trasporto di merci pericolose. 
 * Questa annotazione funge da strato anti-corruzione per garantire che il sistema 
 * accetti solo formati legalmente e semanticamente validi prima ancora di 
 * raggiungere il livello di Service.
 * <p><b>Motore di Validazione (Regole Regex):</b></p>
 * Il controllo è delegato a un'espressione regolare rigorosa ({@code ^(X?\d{2,3})$}) 
 * che impone i seguenti vincoli di formattazione:
 * <ul>
 * <li><b>Lunghezza e composizione base:</b> Deve essere composto da esattamente 2 o 3 cifre numeriche 
 * (es. {@code 33} per liquidi altamente infiammabili, {@code 268} per gas tossici e corrosivi).</li>
 * <li><b>Prefisso di reattività all'acqua (Opzionale):</b> Può essere preceduto dalla lettera maiuscola 
 * {@code 'X'} (es. {@code X88}), che segnala il divieto assoluto di utilizzare acqua sul carico 
 * a causa di reazioni chimiche pericolose.</li>
 * </ul>
 * <i>Nota: Non sono ammessi spazi, caratteri speciali, né l'uso della 'x' minuscola.</i>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Essendo targettizzata su {@code { FIELD, PARAMETER }}, l'annotazione può essere 
 * applicata sia sui campi interni dei DTO (Data Transfer Object) in ingresso, sia 
 * direttamente sui parametri dei Controller REST (es. {@code @PathVariable String kemler}).
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = { KemlerCodeValidator.class })
public @interface ValidatorKemlerCode {
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
	String message() default "Malformed payload: invalid format for the provided Kemler code. Must be 2 or 3 digits, optionally prefixed by 'X'";
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
