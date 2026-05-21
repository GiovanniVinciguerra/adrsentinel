package dev.vinciguerra.adrsentinel.web.annotation.adrclass.validator;

import java.util.regex.Pattern;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorAdrClassCode}.
 * Funge da dogana applicativa per garantire che le stringhe in ingresso rispettino 
 * rigorosamente la nomenclatura formale e legale delle classi di pericolo ADR.
 * <p><b>Design Architetturale (Strict Validation):</b></p>
 * A differenza dei validatori puramente formali che tollerano l'assenza del dato, 
 * questa implementazione adotta un approccio restrittivo: fonde la verifica di 
 * presenza del dato con la validazione sintattica. Un campo annotato con questo 
 * validatore diventa implicitamente obbligatorio.
 * @see ValidatorAdrClassCode
 * @author Giovanni Vinciguerra
 * @version 1.0 (ADR Domain Validator)
 * @since 1.0
 */
public class AdrClassCodeValidator implements ConstraintValidator<ValidatorAdrClassCode, String> {
	/**
	 * Espressione Regolare (Regex) pre-compilata per l'analisi del formato ADR.
	 * <p><b>Ottimizzazione delle Performance:</b></p>
	 * L'istanziazione come costante {@code static final} garantisce che la costosa operazione 
	 * di compilazione dell'automa a stati finiti avvenga una sola volta al caricamento 
	 * della classe (Classloading), azzerando l'overhead computazionale durante le richieste HTTP.
	 * <p><b>Specifiche del Pattern:</b></p>
	 * <ul>
	 * <li>{@code ^(?=.{1,4}$)} : Lookahead che impone una lunghezza stringa tra 1 e 4 caratteri.</li>
	 * <li>{@code \d} : Deve obbligatoriamente iniziare con una cifra numerica.</li>
	 * <li>{@code (\.\d+)?} : Può contenere un blocco decimale opzionale (es. ".1").</li>
	 * <li>{@code [a-zA-Z]?$} : Può terminare con una singola lettera alfabetica opzionale.</li>
	 * </ul>
	 */
	private static final Pattern CLASS_CODE_PATTERN = Pattern.compile("^(?=.{1,4}$)\\d(\\.\\d+)?[a-zA-Z]?$");
	
	/**
	 * Esegue la validazione effettiva sul payload in ingresso (Data Flow).
	 * <p><b>Flusso di Esecuzione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><b>Guard Clause sulla Presenza:</b> Intercetta e respinge istantaneamente valori 
	 * {@code null}, stringhe vuote o composte da soli spazi bianchi (tramite {@code isBlank()}).</li>
	 * <li><b>Pattern Matching:</b> Sottopone la stringa pulita al motore Regex pre-compilato 
	 * per certificarne l'allineamento con la sintassi ADR.</li>
	 * </ol>
	 * @param value Il codice della classe ADR estratto dal Request Payload (es. "3", "5.1", "6.1A").
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se la stringa è presente e rispetta il formato esatto; 
	 * {@code false} se è assente, vuota, o sintatticamente invalida.
	 */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(value == null || value.isBlank())
			return false;
		return CLASS_CODE_PATTERN.matcher(value).matches();
	}
}
