package dev.vinciguerra.adrsentinel.web.annotation.vehicle.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorLicensePlate}.
 * Agisce come filtro di ingresso (Edge Validation) per le targhe dei veicoli nel dominio 
 * dei trasporti ADR, garantendo che i codici identificativi siano obbligatori e rispettino 
 * una semantica alfanumerica coerente.
 * <p><b>Design Architetturale (Tolerant Reader & Robustness Principle):</b></p>
 * A differenza di validatori strettamente rigidi, questa classe implementa il Principio 
 * di Robustezza (Legge di Postel: "sii conservativo in ciò che fai, sii liberale in ciò 
 * che accetti"). Per massimizzare la Developer/User Experience (UX), il validatore è stato 
 * ingegnerizzato per tollerare l'immissione di caratteri minuscoli e separatori visivi 
 * (spazi e trattini), tipicamente usati dall'utenza per favorire la leggibilità 
 * (es. "ek-356-fl" o "EK 356 fL").
 * <i>Nota Architetturale: L'accettazione di questi formati implica che il DTO o il Service 
 * Layer a valle debbano occuparsi della normalizzazione del dato (es. conversione in UpperCase 
 * e rimozione dei separatori) prima dell'indicizzazione a database.</i>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorLicensePlate
 */
public class LicensePlateValidator implements ConstraintValidator<ValidatorLicensePlate, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per l'analisi formale e tollerante della targa.
	 * <p><b>Ottimizzazione e Specifiche del Pattern:</b></p>
	 * L'istanziazione come costante {@code static final} azzera l'overhead computazionale.
	 * Il pattern {@code ^[a-zA-Z0-9 \\-]{4,12}$} accetta stringhe di lunghezza compresa tra 
	 * 4 e 12 caratteri, composte esclusivamente da lettere (maiuscole e minuscole), numeri, 
	 * spazi e trattini. Respinge proattivamente qualsiasi altro carattere speciale potenzialmente 
	 * nocivo.
	 */
	private final static Pattern LICENSE_PLATE_PATTERN = Pattern.compile("^[a-zA-Z0-9 \\-]{4,12}$");
	/**
	 * Esegue l'ispezione della stringa in ingresso per certificarne l'obbligatorietà e 
	 * l'allineamento formale alle regole di tolleranza del sistema.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Pattern Matching):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge immediatamente 
	 * i valori {@code null}, le stringhe vuote o composte esclusivamente da spazi bianchi 
	 * (tramite {@code isBlank()}). Questo assicura che il veicolo sia sempre tracciato.</li>
	 * <li><b>Validazione Sintattica Tollerante:</b> La stringa viene passata al motore Regex 
	 * per certificare che non contenga simboli inattesi (es. tag HTML, punteggiatura non 
	 * autorizzata) e che rientri nei margini dimensionali ammessi, tenendo conto anche 
	 * dello spazio occupato dai separatori.</li>
	 * </ol>
	 * @param value La stringa rappresentante la targa del veicolo estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la targa è presente e composta esclusivamente da caratteri ammessi 
	 * entro i limiti dimensionali (4-12); {@code false} in caso di assenza, formato illecito o 
	 * caratteri speciali vietati.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		return LICENSE_PLATE_PATTERN.matcher(value).matches();
	}
}
