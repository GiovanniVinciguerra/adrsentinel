package dev.vinciguerra.adrsentinel.web.annotation.driver.validator;

import java.util.Set;
import dev.vinciguerra.adrsentinel.db.driver.Driver.DriverApproval;
import dev.vinciguerra.adrsentinel.web.annotation.driver.ValidatorDriverApprovals;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorDriverApprovals}.
 * Ispeziona in profondità una collezione di stringhe per garantire che ogni elemento sia mappabile 
 * nel dizionario di dominio delle abilitazioni ADR del conducente (es. patentino base, cisterne, esplosivi).
 * <p><b>Contesto Architetturale (Anti-Corruption Layer & Edge Validation):</b></p>
 * Questa classe agisce come un firewall logico sul Presentation Layer. Previene anomalie da 
 * <i>Payload Injection</i> scartando stringhe malformate o inesistenti prima che raggiungano 
 * la logica di business, proteggendo i servizi sottostanti (come gli algoritmi di routing 
 * e assegnazione dei carichi) da eccezioni impreviste dovute a dati corrotti.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorDriverApprovals
 */
public class DriverApprovalsValidator implements ConstraintValidator<ValidatorDriverApprovals, Set<String>> {
	/**
	 * Esegue l'ispezione profonda (Deep Inspection) della collezione fornita dal payload.
	 * <p><b>Flusso di Validazione e Regole di Business:</b></p>
	 * <ul>
	 * <li><b>1. Obbligatorietà del Dato (Strict Presence):</b> La collezione non ammette l'assenza ({@code null}). 
	 * L'omissione del nodo JSON associato provoca il fallimento immediato della validazione (<i>Fail-Fast</i>). 
	 * Per rappresentare il caso d'uso di un autista non ancora in possesso di abilitazioni ADR specifiche, 
	 * il client deve fornire esplicitamente un array vuoto ({@code []}).</li>
	 * <li><b>2. Esecuzione Implicita (Empty Set):</b> L'invio di un array vuoto ({@code []}) bypassa 
	 * naturalmente il ciclo iterativo interno. Questa operazione si risolve in tempo costante (O(1)) 
	 * ritornando {@code true}, e validando l'assenza di certificazioni senza alcun overhead computazionale.</li>
	 * <li><b>3. Integrità del Contenuto (Blank Check):</b> Invalida immediatamente il payload qualora 
	 * la collezione includa valori {@code null}, stringhe vuote o sequenze composte esclusivamente 
	 * da spazi vuoti (tramite {@code isBlank()}).</li>
	 * <li><b>4. Risoluzione a Dizionario (Enum Dictionary Match):</b> Effettua un casting sicuro 
	 * verso l'enumerazione di dominio {@link DriverApproval}. L'approccio difensivo tramite blocco 
	 * {@code try-catch} assicura che, al primo identificativo di certificato non riconosciuto 
	 * nel dizionario ADR, l'intero blocco venga respinto (HTTP 400).</li>
	 * </ul>
	 * @param values La collezione di stringhe rappresentanti le omologazioni ADR (Request Payload).
	 * @param context Il contesto di validazione fornito dal framework (Spring/Hibernate Validator).
	 * @return {@code true} se la collezione è istanziata (seppur vuota) e contiene esclusivamente valori Enum validi; 
	 * {@code false} se la collezione è {@code null}, o se presenta elementi nulli, vuoti o non riconosciuti.
	 */
	@Override
	public boolean isValid(Set<String> values, ConstraintValidatorContext context) {
		if(values == null)
			return false;
		for(String value : values) {
			if(value == null || value.trim().isBlank())
				return false;
			try {
				Enum.valueOf(DriverApproval.class, value);
			} catch(IllegalArgumentException error) {
				return false;
			}
		}
		return true;
	}
}
