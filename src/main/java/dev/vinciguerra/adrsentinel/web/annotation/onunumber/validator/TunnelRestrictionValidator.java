package dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator;

import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorTunnelRestriction;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorTunnelRestriction}.
 * Agisce come scudo architetturale (Edge Validation) per il Codice di Restrizione in Galleria 
 * (Tunnel Restriction Code) delle merci pericolose ADR, assicurando che i dati elaborati 
 * dal sistema siano semanticamente validi e sicuri.
 * <p><b>Design Architetturale (Optionality & Type-Safety):</b></p>
 * Nel pieno rispetto del <i>Single Responsibility Principle</i>, questa classe abbraccia 
 * la natura intrinsecamente opzionale del campo nel dominio dei trasporti (non tutte le merci 
 * hanno restrizioni in galleria). Tollerando i valori {@code null}, lascia che l'obbligatorietà 
 * sia gestita da annotazioni esterne qualora il contesto lo richieda. Tuttavia, se il client 
 * fornisce un valore, il validatore impone una ferrea risoluzione a dizionario (Enum Matching), 
 * bloccando le stringhe infette e proteggendo il Service Layer dalle eccezioni a runtime.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorTunnelRestriction
 */
public class TunnelRestrictionValidator implements ConstraintValidator<ValidatorTunnelRestriction, String> {
	/**
	 * Esegue l'ispezione della stringa in ingresso per certificarne la validità strutturale e semantica.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Defense in Depth):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Tolleranza all'Assenza):</b> Intercetta i valori {@code null} 
	 * e restituisce immediatamente {@code true}. Questo passaggio legittima l'assenza del dato, 
	 * prevenendo calcoli inutili e rispettando l'opzionalità del codice galleria.</li>
	 * <li><b>Risoluzione a Dizionario (Enum Matching):</b> Tenta il casting della stringa 
	 * fornita (inclusi tentativi malevoli di invio di stringhe vuote {@code ""}) verso l'enumerazione 
	 * {@code TunnelRestriction}. Se la stringa non corrisponde a una costante definita (es. C, D, C/E), 
	 * l'infrastruttura solleva una {@code IllegalArgumentException}, che viene immediatamente 
	 * intercettata e tradotta in un fallimento di validazione pulito (HTTP 400).</li>
	 * <li><b>Boundary Check (Defense in Depth):</b> Come ultimo strato di difesa, certifica 
	 * matematicamente che la lunghezza della stringa non ecceda i 255 caratteri. Sebbene il 
	 * superamento del controllo precedente renda questa eventualità impossibile, l'istruzione 
	 * garantisce l'assoluta compatibilità con i limiti fisici di archiviazione del database.</li>
	 * </ol>
	 * @param value La stringa rappresentante il codice galleria estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la stringa è assente ({@code null}), oppure se è presente e 
	 * corrisponde esattamente a una costante valida dell'Enum; {@code false} se il dato è 
	 * presente ma non riconosciuto a dizionario (inclusa la stringa vuota).
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return true;
		try {
			Enum.valueOf(TunnelRestriction.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
