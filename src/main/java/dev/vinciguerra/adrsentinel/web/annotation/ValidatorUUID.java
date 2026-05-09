package dev.vinciguerra.adrsentinel.web.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Vincolo di validazione perimetrale (Edge Validation) per l'Identificatore Univoco Universale 
 * (UUID), tipicamente utilizzato come Business Key per i singoli articoli della spedizione (Shipment Item).
 * <p><b>Contesto Architetturale (Sistemi Distribuiti e Sicurezza):</b></p>
 * Nel design delle API moderne, l'esposizione di un UUID rispetto a un classico ID sequenziale 
 * del database (es. 1, 2, 3) è una <i>best practice</i> fondamentale di sicurezza. Previene le 
 * vulnerabilità di tipo IDOR (Insecure Direct Object Reference) rendendo impossibile per un 
 * attaccante indovinare gli ID di altri record. Questa annotazione agisce come "Anti-Corruption Layer", 
 * blindando gli endpoint di lookup o aggiornamento contro tentativi di iniezione o formati corrotti.
 * <p><b>Motore di Validazione (Constraint Composition):</b></p>
 * Sfruttando la composizione di vincoli nativi ({@code @Constraint(validatedBy = {})}), 
 * il validatore orchestra due rigidi livelli di controllo:
 * <ul>
 * <li><b>Esistenza Assoluta ({@code @NotNull}):</b> Assicura che le operazioni specifiche su un 
 * singolo articolo (es. modifica, cancellazione, lettura puntuale) non avvengano mai con 
 * chiavi di ricerca nulle, prevenendo {@code NullPointerException} nel Service.</li>
 * <li><b>Integrità Strutturale ({@code @Pattern}):</b> Attraverso una complessa espressione 
 * regolare, garantisce che la stringa rispetti lo standard canonico UUID (formato 8-4-4-4-12). 
 * La regex impone la presenza dei trattini separatori e accetta esclusivamente caratteri 
 * esadecimali in formato minuscolo.</li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, è progettata per operare sia sui campi dei 
 * Data Transfer Object (DTO), sia direttamente sulle Path Variables esposte dai Controller REST 
 * (es. {@code @PathVariable @ValidatorUUID String itemUUID}).
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Shipment item UUID cannot be null.")
@Pattern(
	regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
	message = "Wrong Shipment item UUID format."
)
public @interface ValidatorUUID {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Invalid Shipment item UUID format or missing.";
	/**
	 * Partiziona l'esecuzione del vincolo associandolo a specifici Validation Groups.
	 * <p>Utile per differenziare i controlli a seconda del contesto (es. Creazione vs Aggiornamento).</p>
	 * In questo caso è lasciato volutamente vuoto.
	 * @return l'array delle classi (gruppi) a cui appartiene questo vincolo.
	 */
	Class<?>[] groups() default {};
	/**
	 * Consente di allegare metadati informativi (Payload) alla violazione del vincolo, 
	 * tipicamente utilizzati per definire il livello di severità dell'errore.
	 * <p>Volutamente lasciato vuoto in questo caso.</p>
	 * @return l'array delle classi payload associate.
	 */
	Class<? extends Payload>[] payload() default {};
}
