package dev.vinciguerra.adrsentinel.web.annotation.onunumber.validator;

import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorTransportCategory;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del vincolo di validazione perimetrale {@link ValidatorTransportCategory}.
 * Agisce come barriera di controllo (Edge Validation) per garantire che la Categoria di Trasporto 
 * (Transport Category) delle merci pericolose ADR sia esplicitamente dichiarata e rientri 
 * nel rigido range normativo.
 * <p><b>Design Architetturale (Strict Validation & Wrapper Types):</b></p>
 * L'utilizzo della classe Wrapper {@code Integer} (invece del primitivo {@code int}) è una 
 * scelta architetturale precisa. Permette al framework di catturare l'effettiva assenza del dato 
 * (tramite valore {@code null}), prevenendo la subdola conversione implicita a {@code 0} 
 * (che nel dominio ADR rappresenterebbe una categoria di trasporto reale e ad altissimo rischio).
 * Accoppiato a un approccio restrittivo (che rifiuta i valori nulli), questo validatore fonde 
 * l'obbligatorietà del parametro con il rispetto dei confini matematici del dominio.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorTransportCategory
 */
public class TransportCategoryValidator implements ConstraintValidator<ValidatorTransportCategory, Integer> {
	/**
	 * Esegue l'ispezione profonda del valore numerico in ingresso per certificarne l'obbligatorietà 
	 * e l'allineamento alle direttive ADR.
	 * <p><b>Flusso di Esecuzione (Fail-Fast):</b></p>
	 * <ol>
	 * <li><b>Guard Clause (Strict Presence Check):</b> Intercetta e respinge istantaneamente i valori 
	 * {@code null}. Questo passaggio garantisce che il dato sia stato volontariamente fornito dal 
	 * client, impedendo che un campo mancante nel JSON venga ignorato o mal interpretato.</li>
	 * <li><b>Domain Boundary Check (Limiti Normativi):</b> Certifica che l'intero fornito 
	 * sia matematicamente compreso nel range inclusivo [0, 4]. Questa regola traduce in codice 
	 * la tabella normativa ADR, che classifica il livello di pericolosità ai fini delle 
	 * esenzioni parziali esattamente con i valori 0, 1, 2, 3 e 4.</li>
	 * </ol>
	 * @param value Il valore intero rappresentante la categoria di trasporto, estratto dal Request Payload.
	 * @param context Il contesto di validazione iniettato dal framework (Spring/Hibernate Validator).
	 * @return {@code true} esclusivamente se il valore è presente (non nullo) e corrisponde a 
	 * 0, 1, 2, 3 o 4; {@code false} in caso di assenza del dato o violazione dei confini numerici.
	 */
	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		return value >= 0 && value <= 4;
	}
}
