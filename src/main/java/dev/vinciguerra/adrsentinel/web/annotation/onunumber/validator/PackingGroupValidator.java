package dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator;

import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PackingGroup;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPackingGroup;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorPackingGroup}.
 * Agisce come scudo architetturale (Edge Validation) per il Gruppo di Imballaggio (Packing Group) 
 * delle merci pericolose ADR, garantendo che i dati forniti siano sicuri e semanticamente corretti.
 * <p><b>Design Architetturale (Optionality & Type-Safety):</b></p>
 * A differenza dei validatori restrittivi, questa classe abbraccia la natura opzionale 
 * del campo nel Dominio. Tollerando i valori {@code null}, demanda l'eventuale controllo 
 * di obbligatorietà ad annotazioni esterne (es. {@code @NotNull}). Tuttavia, se il dato 
 * viene fornito, il validatore impone una ferrea risoluzione a dizionario (Enum Matching), 
 * proteggendo il Service Layer da stringhe non riconosciute e prevenendo le classiche 
 * eccezioni di conversione a runtime.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorPackingGroup
 */
public class PackingGroupValidator implements ConstraintValidator<ValidatorPackingGroup, String> {
	/**
	 * Esegue l'ispezione della stringa in ingresso per certificarne la validità strutturale e semantica.
	 * <p><b>Flusso di Esecuzione (Fail-Fast & Defense in Depth):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Tolleranza all'Assenza):</b> Intercetta i valori {@code null} 
	 * e restituisce immediatamente {@code true}. Questo bypass legittima l'assenza del dato, 
	 * rispettando l'opzionalità del gruppo di imballaggio per alcune classi ADR.</li>
	 * <li><b>Risoluzione a Dizionario (Enum Matching):</b> Sottopone il valore (inclusi 
	 * eventuali tentativi di invio di stringhe vuote {@code ""}) al casting verso l'enumerazione 
	 * {@code PackingGroup}. Se il valore non corrisponde esattamente a una costante definita 
	 * (es. I, II, III), viene sollevata una {@code IllegalArgumentException}, che il blocco 
	 * catch converte elegantemente in un fallimento di validazione (HTTP 400).</li>
	 * <li><b>Boundary Check (Defense in Depth):</b> Come ultimo strato di sicurezza, verifica 
	 * matematicamente che la lunghezza della stringa non superi i 255 caratteri. Pur essendo 
	 * un limite implicitamente rispettato dal superamento del check sull'Enum, questa istruzione 
	 * certifica la totale compatibilità con i limiti fisici del database (es. colonna VARCHAR).</li>
	 * </ol>
	 * @param value La stringa rappresentante il gruppo di imballaggio estratta dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la stringa è assente ({@code null}), oppure se è presente e 
	 * corrisponde a una costante valida dell'Enum; {@code false} se il dato è presente ma non 
	 * riconosciuto a dizionario (inclusa la stringa vuota).
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null)
			return true;
		try {
			Enum.valueOf(PackingGroup.class, value);
		} catch(IllegalArgumentException error) {
			return false;
		}
		return value.length() <= 255;
	}
}
