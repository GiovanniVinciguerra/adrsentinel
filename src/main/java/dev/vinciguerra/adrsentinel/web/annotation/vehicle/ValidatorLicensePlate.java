package dev.vinciguerra.adrsentinel.web.annotation.vehicle;

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
 * Vincolo di validazione perimetrale (Edge Validation) per la Targa del Veicolo 
 * (License Plate) assegnato al trasporto logistico.
 * <p><b>Contesto Architetturale (Identificazione Flotta e Normativa ADR):</b></p>
 * Nel dominio dei trasporti di merci pericolose, la targa del veicolo non è un semplice 
 * attributo testuale, ma un identificatore legale vincolante, essenziale per la tracciabilità, 
 * i controlli doganali e l'associazione con i documenti di trasporto (Lettera di Vettura). 
 * Questa annotazione agisce come "Anti-Corruption Layer", assicurando che il sistema 
 * acquisisca esclusivamente targhe formalmente valide e normalizzate, respingendo input 
 * sporchi derivanti da digitazioni errate o da sistemi di terze parti non allineati.
 * <p><b>Motore di Validazione (Constraint Composition e Normalizzazione):</b></p>
 * Sfruttando la delega ai vincoli nativi ({@code @Constraint(validatedBy = {})}), 
 * il validatore impone un rigoroso standard di formattazione:
 * <ul>
 * <li><b>Esistenza ({@code @NotNull}):</b> Garantisce che l'identificativo del mezzo 
 * sia sempre fornito. Un trasporto logistico non può esistere senza un veicolo fisico.</li>
 * <li><b>Integrità e Standardizzazione ({@code @Pattern}):</b> Attraverso la regex 
 * {@code ^[A-Z0-9]{4,10}$}, impone regole ferree e internazionali:
 * <ul>
 * <li><b>Alfanumerico Maiuscolo:</b> Accetta esclusivamente lettere maiuscole e numeri, 
 * vietando esplicitamente caratteri speciali, spaziature (es. "AB 123 CD") o 
 * trattini (es. "AB-123-CD"). Questo azzera le ambiguità nel database.</li>
 * <li><b>Dimensionamento:</b> Richiede una lunghezza compresa tra 4 e 10 caratteri, 
 * coprendo la quasi totalità dei formati di targa civili e commerciali a livello europeo.</li>
 * </ul>
 * </li>
 * </ul>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, è progettata per operare su campi di tipo 
 * {@code String} all'interno dei Data Transfer Object (es. {@code VehicleDTO}, {@code ShipmentRequestDTO}) 
 * o direttamente sui parametri esposti nei Controller REST.
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "License plate cannot be null.")
@Pattern(
	regexp = "^[A-Z0-9]{4,10}$",
	message = "License plate must be between 4 and 10 characters and contain only uppercase letters and numbers"
)
public @interface ValidatorLicensePlate {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "License plate number code format or missing code.";
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
