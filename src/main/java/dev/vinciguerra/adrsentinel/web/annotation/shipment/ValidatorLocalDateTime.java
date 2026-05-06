package dev.vinciguerra.adrsentinel.web.annotation.shipment;

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
 * Vincolo di validazione perimetrale (Edge Validation) per i marcatori temporali 
 * complessi (Data e Ora) formattati come stringhe.
 * <p><b>Contesto Architetturale (Logistica di Precisione):</b></p>
 * Nel dominio dei trasporti ADR, tracciare l'esatto momento cronologico di un evento 
 * (es. partenza del veicolo, orario di consegna, momento dell'incidente) è un requisito 
 * legale e operativo critico. Questa annotazione impone il rispetto del formato 
 * internazionale ISO 8601 per le date e ore locali ({@code YYYY-MM-DDThh:mm:ss}), 
 * agendo da "Anti-Corruption Layer" contro formati temporali non standard o parziali.
 * <p><b>Motore di Validazione (Constraint Composition e Regex Avanzata):</b></p>
 * Sfruttando la delegazione ai vincoli nativi ({@code @Constraint(validatedBy = {})}), 
 * il validatore orchestra due livelli di controllo:
 * <ul>
 * <li><b>Esistenza ({@code @NotNull}):</b> Blocca i payload privi di marcatore temporale, 
 * garantendo che l'informazione cronologica sia sempre presente.</li>
 * <li><b>Integrità Strutturale ({@code @Pattern}):</b> Valida sintatticamente la stringa 
 * tramite una regex che estende il controllo della data con la componente oraria:
 * <ul>
 * <li>Componente Data: {@code ^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])} (Anno-Mese-Giorno validi).</li>
 * <li>Separatore ISO: {@code T} (Il carattere letterale che separa la data dall'ora).</li>
 * <li>Ore: {@code ([01]\d|2[0-3])} (Limita il range da 00 a 23).</li>
 * <li>Minuti e Secondi: {@code :[0-5]\d:[0-5]\d$} (Limita il range da 00 a 59 per entrambi).</li>
 * </ul>
 * </li>
 * </ul>
 * <i>Nota: La regex richiede esplicitamente i secondi (es. 14:30:00). Non supporta i millisecondi 
 * o gli indicatori di fuso orario (Timezone Offset come 'Z' o '+02:00'). La conversione 
 * logica finale sarà demandata al parsing verso {@link java.time.LocalDateTime}.</i>
 * <p><b>Applicabilità Architetturale:</b></p>
 * Targettizzata per {@code { FIELD, PARAMETER }}, questa annotazione deve essere applicata 
 * <b>esclusivamente su campi di tipo {@code String}</b> all'interno dei DTO per evitare conflitti 
 * a runtime con la deserializzazione automatica di Jackson.
 * @return Il messaggio di errore predefinito o personalizzato in caso di fallimento della validazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@Constraint(validatedBy = {})
@NotNull(message = "Local date time cannot be null")
@Pattern(
	regexp = "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])T([01]\\d|2[0-3]):[0-5]\\d:[0-5]\\d$",
	message = "Wrong date format. Correct: yyyy-mm-ggThh:MM:ss"
)
public @interface ValidatorLocalDateTime {
	/**
	 * Il messaggio di errore unificato che verrà restituito nel payload di risposta (es. HTTP 400) 
	 * in caso di fallimento di uno qualsiasi dei vincoli sottostanti.
	 * @return il messaggio testuale che descrive chiaramente sia l'obbligatorietà che il limite dimensionale.
	 */
	String message() default "Wrong format or missing date.";
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
