package org.openmrs.api.db.hibernate;

import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.SessionFactory;
import org.hibernate.StaleObjectStateException;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptDerived;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNumeric;
import org.openmrs.ConceptProposal;
import org.openmrs.ConceptSet;
import org.openmrs.ConceptSetDerived;
import org.openmrs.ConceptSource;
import org.openmrs.ConceptSynonym;
import org.openmrs.ConceptWord;
import org.openmrs.Drug;
import org.openmrs.DrugIngredient;
import org.openmrs.User;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ConceptDAO;
import org.openmrs.api.db.DAOException;
import org.openmrs.util.OpenmrsConstants;

public class HibernateConceptDAO implements
		ConceptDAO {

	protected final Log log = LogFactory.getLog(getClass());

	/**
	 * Hibernate session factory
	 */
	private SessionFactory sessionFactory;
	
	public HibernateConceptDAO() { }
	
	/**
	 * Set session factory
	 * 
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) { 
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#createConcept(org.openmrs.Concept)
	 */
	public void createConcept(Concept concept) throws DAOException {
		this.createConcept(concept, false);
	}

	public void createConcept(Concept concept, boolean isForced) throws DAOException {
		modifyCollections(concept);

		sessionFactory.getCurrentSession().save(concept);
		
		Context.getAdministrationService().updateConceptWord(concept, isForced);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#createConcept(org.openmrs.ConceptNumeric)
	 */
	public void createConcept(ConceptNumeric concept) throws DAOException {
		this.createConcept(concept, false);
	}
	
	public void createConcept(ConceptNumeric concept, boolean isForced) throws DAOException {
		modifyCollections(concept);
		
		sessionFactory.getCurrentSession().save(concept);
		
		Context.getAdministrationService().updateConceptWord(concept, isForced);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#deleteConcept(org.openmrs.Concept)
	 */
	public void deleteConcept(Concept concept) throws APIException {
		sessionFactory.getCurrentSession().createQuery("delete from ConceptWord where concept_id = :c")
					.setInteger("c", concept.getConceptId())
					.executeUpdate();
			
		sessionFactory.getCurrentSession().delete(concept);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConcept(java.lang.Integer)
	 */
	public Concept getConcept(Integer conceptId) throws APIException {
		return (Concept)sessionFactory.getCurrentSession().get(Concept.class, conceptId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConcept(java.lang.Integer)
	 */
	public ConceptAnswer getConceptAnswer(Integer conceptAnswerId) throws APIException {
		return (ConceptAnswer)sessionFactory.getCurrentSession().get(ConceptAnswer.class, conceptAnswerId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConcepts(java.lang.String, java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public List<Concept> getConcepts(String sort, String dir) throws APIException {
		String sql = "from Concept concept";
		
		try {
			Concept.class.getDeclaredField(sort);
		}
		catch (NoSuchFieldException e) {
			try {
				ConceptName.class.getDeclaredField(sort);
				sort = "names." + sort;
			}
			catch (NoSuchFieldException e2) {
				sort = "conceptId";
			}
		}
		
		sql += " order by concept." + sort;
		if (dir.equals("desc"))
			sql += " desc";
		else
			sql += " asc";
		
		Query query = sessionFactory.getCurrentSession().createQuery(sql);
		 
		return query.list();
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#updateConcept(org.openmrs.Concept)
	 */
	public void updateConcept(Concept concept) {
		this.updateConcept(concept, false);
	}

	public void updateConcept(Concept concept, boolean isForced) {
		
		if (concept.getConceptId() == null)
			createConcept(concept);
		else {
			modifyCollections(concept);
			sessionFactory.getCurrentSession().merge(concept);
			Context.getAdministrationService().updateConceptWord(concept, isForced);
		}
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#updateConcept(org.openmrs.ConceptNumeric)
	 */
	public void updateConcept(ConceptNumeric concept) {
		this.updateConcept(concept, false);
	}
	
	public void updateConcept(ConceptNumeric concept, boolean isForced) {
		
		if (concept.getConceptId() == null)
			createConcept(concept);
		else {
			modifyCollections(concept);
			try {
				sessionFactory.getCurrentSession().update(concept);
				// force saving the concept now (not at end of session)
				sessionFactory.getCurrentSession().flush();
				log.error("after updating concept");
			}
			catch (StaleObjectStateException sose) {
				log.error("after error");
				sessionFactory.getCurrentSession().clear();
				Query query = sessionFactory.getCurrentSession().createQuery("insert into ConceptNumeric (conceptId) select c.conceptId from Concept c where c.conceptId = :id)");
					query.setInteger("id", concept.getConceptId());
					query.executeUpdate();
				sessionFactory.getCurrentSession().merge(concept);
				log.error("after error after updating concept");
			}
			
			Context.getAdministrationService().updateConceptWord(concept, isForced);
		}
	}
	

	/**
	 * @see org.openmrs.api.db.DrugService#createDrug(org.openmrs.Drug)
	 */
	public void createDrug(Drug drug) throws DAOException {
		if (drug.getCreator() == null)
			drug.setCreator(Context.getAuthenticatedUser());
		if (drug.getDateCreated() == null)
			drug.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().save(drug);
	}
	
	/**
	 * @see org.openmrs.api.db.DrugService#updateDrug(org.openmrs.Drug)
	 */
	public void updateDrug(Drug drug) throws DAOException {
		if (drug.getDrugId() == null)
			createDrug(drug);
		else {
			if (drug.getCreator() == null)
				drug.setCreator(Context.getAuthenticatedUser());
			if (drug.getDateCreated() == null)
				drug.setDateCreated(new Date());
			
			sessionFactory.getCurrentSession().update(drug);
		}	
	}
	
	/**
	 * TODO This should be moved to the service layer
	 * 
	 * @param c
	 */
	protected void modifyCollections(Concept c) {
		
		User authUser = Context.getAuthenticatedUser();
		Date timestamp = new Date();
		
		if (c.getCreator() == null) {
			c.setCreator(authUser);
			if (c.getDateCreated() == null)
				c.setDateCreated(timestamp);
		}
		else {
			c.setChangedBy(authUser);
			c.setDateChanged(timestamp);
		}
		
		if (c.getNames() != null) {
			for (ConceptName cn : c.getNames()) {
				if (cn.getCreator() == null ) {
					cn.setCreator(authUser);
					if (cn.getDateCreated() == null)
						cn.setDateCreated(timestamp);
				}
			}
		}
		for (ConceptSynonym syn : c.getSynonyms()) {
			if (syn.getCreator() == null ) {
				syn.setCreator(authUser);
				if (syn.getDateCreated() == null)
					syn.setDateCreated(timestamp);
			}
			syn.setConcept(c);
		}
		if (c.getConceptSets() != null) {
			for (ConceptSet set : c.getConceptSets()) {
				if (set.getCreator() == null ) {
					set.setCreator(authUser);
					if (set.getDateCreated() == null)
						set.setDateCreated(timestamp);
				}
				set.setConceptSet(c);
			}
		}
		if (c.getAnswers(true) != null) {
			for (ConceptAnswer ca : c.getAnswers(true)) {
				if (ca.getCreator() == null ) {
					ca.setCreator(authUser);
					if (ca.getDateCreated() == null)
						ca.setDateCreated(timestamp);
				}
				ca.setConcept(c);
			}
		}
		/*
		if (c.getConceptNumeric() != null) {
			ConceptNumeric cn = c.getConceptNumeric();
			if (cn.getCreator() == null) {
				cn.setCreator(authUser);
				cn.setDateCreated(timestamp);
			}
			cn.setConcept(c);
			cn.setChangedBy(authUser);
			cn.setDateChanged(timestamp);
		}
		*/

	}

	/**
	 * @see org.openmrs.api.db.ConceptService#voidConcept(org.openmrs.Concept, java.lang.String)
	 */
	public void voidConcept(Concept concept, String reason) {
		this.voidConcept(concept, reason, false);
	}
	
	public void voidConcept(Concept concept, String reason, boolean isForced) {
		concept.setRetired(false);
		updateConcept(concept, isForced);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptsByName(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public List<Concept> getConceptsByName(String name) {
		Query query = sessionFactory.getCurrentSession().createQuery("select c from Concept c join c.names names where names.name like '%' || ? || '%'");
		query.setString(0, name);
		List<Concept> concepts = query.list();
		
		return concepts;
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptByName(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public Concept getConceptByName(String name) {		
		Query query = sessionFactory.getCurrentSession().createQuery("select c from Concept c join c.names names where names.name = ?");
		query.setString(0, name);
		List<Concept> concepts = query.list();
		
		int size = concepts.size(); 
		if (size > 0){
			if (size > 1)
				log.warn("Multiple concepts found for '" + name + "'");			
			return concepts.get(0);
		}
		
		return null;
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getDrug(java.lang.Integer)
	 */
	public Drug getDrug(Integer drugId) {
		return (Drug)sessionFactory.getCurrentSession().get(Drug.class, drugId);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getDrug(java.lang.String)
	 */
	public Drug getDrug(String drugName) {
		return (Drug) sessionFactory.getCurrentSession().createQuery("from Drug d where d.name = :name").setString("name", drugName).uniqueResult();
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getDrugs()
	 */
	@SuppressWarnings("unchecked")
	public List<Drug> getDrugs() {
		return sessionFactory.getCurrentSession().createQuery("from Drug order by name").list();
	}
	
	
	/**
	 * @see org.openmrs.api.db.ConceptService#findDrugs(java.lang.String,boolean)
	 */
	@SuppressWarnings("unchecked")
	public List<Drug> findDrugs(String phrase, boolean includeRetired) {
		List<String> words = ConceptWord.getUniqueWords(phrase); //assumes getUniqueWords() removes quote(') characters.  (otherwise we would have a security leak)
		
		List<Drug> conceptDrugs = new Vector<Drug>();
		
		if (words.size() > 0) {
		
			Criteria searchCriteria = sessionFactory.getCurrentSession().createCriteria(Drug.class, "drug");
			if (includeRetired == false) {
				searchCriteria.add(Expression.eq("drug.voided", false));
			}
			Iterator<String> word = words.iterator();
			searchCriteria.add(Expression.like("name", word.next(), MatchMode.ANYWHERE));
			while (word.hasNext()) {
				String w = word.next();
				log.debug(w);
				searchCriteria.add(Expression.like("name", w, MatchMode.ANYWHERE));
			}
			searchCriteria.addOrder(Order.asc("drug.concept"));
			conceptDrugs = searchCriteria.list();
		}

		return conceptDrugs;
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getDrugs(org.openmrs.Concept)
	 */
	@SuppressWarnings("unchecked")
	public List<Drug> getDrugs(Concept concept) {
		return sessionFactory.getCurrentSession().createCriteria(Drug.class)
			.add(Expression.eq("concept", concept)).list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptClass(java.lang.Integer)
	 */
	public ConceptClass getConceptClass(Integer i) {
		return (ConceptClass)sessionFactory.getCurrentSession().get(ConceptClass.class, i);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptClassByName(java.lang.String)
	 */
	public ConceptClass getConceptClassByName(String name) {
		Criteria crit = sessionFactory.getCurrentSession().createCriteria(ConceptClass.class)
			.add(Expression.eq("name", name));
		
		if (crit.list().size() < 1) {
			log.warn("No concept class found with name: " + name);
			return null;
		}
		
		return (ConceptClass)crit.list().get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptClasses()
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptClass> getConceptClasses() {
		return sessionFactory.getCurrentSession().createQuery("from ConceptClass cc order by cc.name").list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptDatatype(java.lang.Integer)
	 */
	public ConceptDatatype getConceptDatatype(Integer i) {
		return (ConceptDatatype)sessionFactory.getCurrentSession().get(ConceptDatatype.class, i);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptDatatypeByName(java.lang.String)
	 */
	public ConceptDatatype getConceptDatatypeByName(String name) {
		Criteria crit = sessionFactory.getCurrentSession().createCriteria(ConceptDatatype.class)
			.add(Expression.eq("name", name));
		
		if (crit.list().size() < 1) {
			log.warn("No concept datatype found with name: " + name);
			return null;
		}
		
		return (ConceptDatatype)crit.list().get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptDatatypes()
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptDatatype> getConceptDatatypes() {
		return sessionFactory.getCurrentSession().createQuery("from ConceptDatatype cd order by cd.name").list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptNumeric(java.lang.Integer)
	 */
	public ConceptNumeric getConceptNumeric(Integer i) {
		ConceptNumeric cn;
		Object obj = sessionFactory.getCurrentSession().get(ConceptNumeric.class, i);
		// If Concept has already been read & cached, we may get back a Concept instead of
		// ConceptNumeric.  If this happens, we need to clear the object from the cache
		// and re-fetch it as a ConceptNumeric
		if (obj != null && !obj.getClass().equals(ConceptNumeric.class)) {
			sessionFactory.getCurrentSession().evict(obj); // remove from cache
			// session.get() did not work here, we need to perform a query to get a ConceptNumeric
			Query query = sessionFactory.getCurrentSession().createQuery("from ConceptNumeric where conceptId = :conceptId")
				.setParameter("conceptId", i);
			obj = query.uniqueResult();
		}
		cn = (ConceptNumeric)obj;

		return cn;
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptSets(java.lang.Integer)
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptSet> getConceptSets(Concept concept) {
		return sessionFactory.getCurrentSession().createCriteria(ConceptSet.class)
					.add(Restrictions.eq("conceptSet", concept))
					.addOrder(Order.asc("sortWeight"))
					.list();
	}
	
	/**
	 * @see org.openmrs.api.ConceptService#getSetsContainingConcept(org.openmrs.Concept)
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptSet> getSetsContainingConcept(Concept concept) {
		return sessionFactory.getCurrentSession().createCriteria(ConceptSet.class)
					.add(Restrictions.eq("concept", concept))
					.list();
	}

	// TODO below are functions worthy of a second tier
	
	/**
	 * @see org.openmrs.api.db.ConceptService#findConcepts(java.lang.String,java.util.Locale,boolean)
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptWord> findConcepts(String phrase, Locale loc, boolean includeRetired, 
			List<ConceptClass> requireClasses, List<ConceptClass> excludeClasses,
			List<ConceptDatatype> requireDatatypes, List<ConceptDatatype> excludeDatatypes) 
			{
		String locale = loc.getLanguage().substring(0, 2);		//only get language portion of locale
		List<String> words = ConceptWord.getUniqueWords(phrase); //assumes getUniqueWords() removes quote(') characters.  (otherwise we would have a security leak)
		
		List<ConceptWord> conceptWords = new Vector<ConceptWord>();
		
		if (words.size() > 0) {
		
			Criteria searchCriteria = sessionFactory.getCurrentSession().createCriteria(ConceptWord.class, "cw1");
			searchCriteria.add(Restrictions.eq("locale", locale));
			if (includeRetired == false) {
				searchCriteria.createAlias("concept", "concept");
				searchCriteria.add(Expression.eq("concept.retired", false));
			}
			Iterator<String> word = words.iterator();
			searchCriteria.add(Expression.like("word", word.next(), MatchMode.START));
			Conjunction junction = Expression.conjunction();
			while (word.hasNext()) {
				String w = word.next();
				log.debug(w);
				DetachedCriteria crit = DetachedCriteria.forClass(ConceptWord.class)
							.setProjection(Property.forName("concept"))
							.add(Expression.eqProperty("concept", "cw1.concept"))
							.add(Restrictions.like("word", w, MatchMode.START))
							.add(Restrictions.eq("locale", locale));
				junction.add(Subqueries.exists(crit));
			}
			searchCriteria.add(junction);
			
			if (requireClasses.size() > 0)
				searchCriteria.add(Expression.in("concept.conceptClass", requireClasses));
			
			if (excludeClasses.size() > 0)
				searchCriteria.add(Expression.not(Expression.in("concept.conceptClass", excludeClasses)));
			
			if (requireDatatypes.size() > 0)
				searchCriteria.add(Expression.in("concept.datatype", requireDatatypes));
			
			if (excludeDatatypes.size() > 0)
				searchCriteria.add(Expression.not(Expression.in("concept.datatype", excludeDatatypes)));
			
			
			searchCriteria.addOrder(Order.asc("synonym"));
			conceptWords = searchCriteria.list();
		}
		
		log.debug("ConceptWords found: " + conceptWords.size());
		
		//TODO this is a bit too much pre/post processing to be in the persistence layer.
		// consider moving to different layer (eg: logic).
		
		return conceptWords;
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#findConcepts(java.lang.String,java.util.Locale,org.openmrs.Concept,boolean)
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptWord> findConceptAnswers(String phrase, Locale loc, Concept concept, boolean includeRetired) {
		String locale = loc.getLanguage().substring(0, 2);		//only get language portion of locale
		List<String> words = ConceptWord.getUniqueWords(phrase); //assumes getUniqueWords() removes quote(') characters.  (otherwise we would have a security leak)
		
		// default return list
		List<ConceptWord> conceptWords = new Vector<ConceptWord>();
		
		// these are the answers to restrict on
		List<Concept> answers = new Vector<Concept>();
		
		if (concept.getAnswers() != null)
			for (ConceptAnswer conceptAnswer : concept.getAnswers()) {
				answers.add(conceptAnswer.getAnswerConcept());
			}
		
		// by default, we will return all of the concept's answers
		// however, if there are no answers, return nothing by default
		if (words.size() > 0 || !answers.isEmpty()) {
			Criteria searchCriteria = sessionFactory.getCurrentSession().createCriteria(ConceptWord.class, "cw1");
			searchCriteria.add(Restrictions.eq("locale", locale));
			if (includeRetired == false) {
				searchCriteria.createAlias("concept", "concept");
				searchCriteria.add(Expression.eq("concept.retired", false));
			}
			
			// Only modification from standard word search
			// Only restrict on answers if there are any
			if (!answers.isEmpty())
				searchCriteria.add(Expression.in("cw1.concept", answers));
		
			// if the user typed in a phrase, restrict further
			if (words.size() > 0) {
				Iterator<String> word = words.iterator();
				searchCriteria.add(Expression.like("word", word.next(), MatchMode.START));
				Conjunction junction = Expression.conjunction();
				while (word.hasNext()) {	// add 'and' expression for _each word_ in search phrase
					String w = word.next();
					log.debug(w);
					DetachedCriteria crit = DetachedCriteria.forClass(ConceptWord.class)
								.setProjection(Property.forName("concept"))
								.add(Expression.eqProperty("concept", "cw1.concept"))
								.add(Restrictions.like("word", w, MatchMode.START))
								.add(Restrictions.eq("locale", locale));
					junction.add(Subqueries.exists(crit));
				}
				searchCriteria.add(junction);
			}
		
			searchCriteria.addOrder(Order.asc("synonym"));
			conceptWords = searchCriteria.list();
		}
		
		return conceptWords;
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getQuestionsForAnswer(org.openmrs.Concept)
	 */
	@SuppressWarnings("unchecked")
	public List<Concept> getQuestionsForAnswer(Concept concept) {
		// TODO broken until Hibernate fixes component and HQL code
		String q = "select c from Concept c join c.answers ca where ca.answerConcept = :answer";
		Query query = sessionFactory.getCurrentSession().createQuery(q);
		query.setParameter("answer", concept);
		
		return query.list();
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getNextConcept(org.openmrs.Concept, java.lang.Integer)
	 */
	@SuppressWarnings("unchecked")
	public Concept getPrevConcept(Concept c) {
		Integer i = c.getConceptId();
		
		List<Concept> concepts = sessionFactory.getCurrentSession().createCriteria(Concept.class)
				.add(Expression.lt("conceptId", i))
				.addOrder(Order.desc("conceptId"))
				.setFetchSize(1)
				.list();
		
		if (concepts.size() < 1)
			return null;
		return concepts.get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getNextConcept(org.openmrs.Concept, java.lang.Integer)
	 */
	@SuppressWarnings("unchecked")
	public Concept getNextConcept(Concept c) {
		Integer i = c.getConceptId();
		
		List<Concept> concepts = sessionFactory.getCurrentSession().createCriteria(Concept.class)
				.add(Expression.gt("conceptId", i))
				.addOrder(Order.asc("conceptId"))
				.setFetchSize(1)
				.list();

		if (concepts.size() < 1)
			return null;
		return concepts.get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptProposals(boolean)
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptProposal> getConceptProposals(boolean includeCompleted) throws APIException {
		Criteria crit = sessionFactory.getCurrentSession().createCriteria(ConceptProposal.class);
		
		if (includeCompleted == false) {
			crit.add(Expression.eq("state", OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED));
		}
		
		crit.addOrder(Order.asc("originalText"));
		
		return crit.list();
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#getConceptProposal(java.lang.Integer)
	 */
	public ConceptProposal getConceptProposal(Integer conceptProposalId) throws APIException {
		return (ConceptProposal)sessionFactory.getCurrentSession().get(ConceptProposal.class, conceptProposalId);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#findMatchingConceptProposals(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public List<ConceptProposal> findMatchingConceptProposals(String text) throws APIException {
		Criteria crit = sessionFactory.getCurrentSession().createCriteria(ConceptProposal.class);
		crit.add(Expression.eq("state", OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED));
		crit.add(Expression.eq("originalText", text));
		
		return crit.list();
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptService#findProposedConcepts(java.lang.String)
	 */
	@SuppressWarnings("unchecked")
	public List<Concept> findProposedConcepts(String text) throws APIException {
		Criteria crit = sessionFactory.getCurrentSession().createCriteria(ConceptProposal.class);
		crit.add(Expression.ne("state", OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED));
		crit.add(Expression.eq("originalText", text));
		crit.add(Expression.isNotNull("mappedConcept"));
		crit.setProjection(Projections.distinct(Projections.property("mappedConcept")));
		
		return crit.list();
	}
	
	public void proposeConcept(ConceptProposal conceptProposal) throws APIException {
		sessionFactory.getCurrentSession().save(conceptProposal);
	}
	
	public Integer getNextAvailableId() {
		String sql = "select min(concept_id+1) as concept_id from concept where (concept_id+1) not in (select concept_id from concept)";
		
		Query query = sessionFactory.getCurrentSession().createSQLQuery(sql);
		
		BigInteger big = (BigInteger)query.uniqueResult();
		
		return new Integer(big.intValue());
	}
	
	@SuppressWarnings("unchecked")
	public List<Concept> getConceptsByClass(ConceptClass cc) {
		Criteria crit = sessionFactory.getCurrentSession().createCriteria(Concept.class);
		crit.add(Expression.eq("conceptClass", cc));
		crit.add(Expression.eq("retired", false));
		return crit.list();
	}
	
	public List<Concept> getConceptsWithDrugsInFormulary() {
		Query query = sessionFactory.getCurrentSession().createQuery("select distinct concept from Drug where voided = false");
		return query.list();
	}

	/**
     * @see org.openmrs.api.db.ConceptDAO#getConceptByGuid(java.lang.String)
     */
    public Concept getConceptByGuid(String guid) {
		return (Concept) sessionFactory.getCurrentSession().createQuery("from Concept c where c.guid = :guid").setString("guid", guid).uniqueResult();
    }

	/**
     * @see org.openmrs.api.db.ConceptDAO#getConceptClassByGuid(java.lang.String)
     */
    public ConceptClass getConceptClassByGuid(String guid) {
		return (ConceptClass) sessionFactory.getCurrentSession().createQuery("from ConceptClass cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptAnswer getConceptAnswerByGuid(String guid) {
		return (ConceptAnswer) sessionFactory.getCurrentSession().createQuery("from ConceptAnswer cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptDerived getConceptDerivedByGuid(String guid) {
		return (ConceptDerived) sessionFactory.getCurrentSession().createQuery("from ConceptDerived cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptName getConceptNameByGuid(String guid) {
		return (ConceptName) sessionFactory.getCurrentSession().createQuery("from ConceptName cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptSet getConceptSetByGuid(String guid) {
		return (ConceptSet) sessionFactory.getCurrentSession().createQuery("from ConceptSet cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptSetDerived getConceptSetDerivedByGuid(String guid) {
		return (ConceptSetDerived) sessionFactory.getCurrentSession().createQuery("from ConceptSetDerived cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptSource getConceptSourceByGuid(String guid) {
		return (ConceptSource) sessionFactory.getCurrentSession().createQuery("from ConceptSource cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptSynonym getConceptSynonymByGuid(String guid) {
		return (ConceptSynonym) sessionFactory.getCurrentSession().createQuery("from ConceptSynonym cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public ConceptWord getConceptWordByGuid(String guid) {
		return (ConceptWord) sessionFactory.getCurrentSession().createQuery("from ConceptWord cc where cc.guid = :guid").setString("guid", guid).uniqueResult();
    }

	/**
     * @see org.openmrs.api.db.ConceptDAO#getConceptDatatypeByGuid(java.lang.String)
     */
    public ConceptDatatype getConceptDatatypeByGuid(String guid) {
		return (ConceptDatatype) sessionFactory.getCurrentSession().createQuery("from ConceptDatatype cd where cd.guid = :guid").setString("guid", guid).uniqueResult();
    }

	/**
     * @see org.openmrs.api.db.ConceptDAO#getConceptNumericByGuid(java.lang.String)
     */
    public ConceptNumeric getConceptNumericByGuid(String guid) {
		return (ConceptNumeric) sessionFactory.getCurrentSession().createQuery("from ConceptNumeric cn where cn.guid = :guid").setString("guid", guid).uniqueResult();
    }

	/**
     * @see org.openmrs.api.db.ConceptDAO#getConceptProposalByGuid(java.lang.String)
     */
    public ConceptProposal getConceptProposalByGuid(String guid) {
		return (ConceptProposal) sessionFactory.getCurrentSession().createQuery("from ConceptProposal cp where cp.guid = :guid").setString("guid", guid).uniqueResult();
    }

	/**
     * @see org.openmrs.api.db.ConceptDAO#getDrugByGuid(java.lang.String)
     */
    public Drug getDrugByGuid(String guid) {
		return (Drug) sessionFactory.getCurrentSession().createQuery("from Drug d where d.guid = :guid").setString("guid", guid).uniqueResult();
    }

    public DrugIngredient getDrugIngredientByGuid(String guid) {
		return (DrugIngredient) sessionFactory.getCurrentSession().createQuery("from DrugIngredient d where d.guid = :guid").setString("guid", guid).uniqueResult();
    }

	/**
	 * @see org.openmrs.api.db.ConceptAnswerService#createConceptAnswer(org.openmrs.ConceptAnswer)
	 */
	public void createConceptAnswer(ConceptAnswer conceptAnswer) throws DAOException {
		if (conceptAnswer.getCreator() == null)
			conceptAnswer.setCreator(Context.getAuthenticatedUser());
		if (conceptAnswer.getDateCreated() == null)
			conceptAnswer.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().save(conceptAnswer);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptAnswerService#updateConceptAnswer(org.openmrs.ConceptAnswer)
	 */
	public void updateConceptAnswer(ConceptAnswer conceptAnswer) throws DAOException {
		if (conceptAnswer.getConceptAnswerId() == null)
			createConceptAnswer(conceptAnswer);
		else {
			if (conceptAnswer.getCreator() == null)
				conceptAnswer.setCreator(Context.getAuthenticatedUser());
			if (conceptAnswer.getDateCreated() == null)
				conceptAnswer.setDateCreated(new Date());
			
			sessionFactory.getCurrentSession().update(conceptAnswer);
		}	
	}

	/**
	 * @see org.openmrs.api.db.ConceptNameService#createConceptName(org.openmrs.ConceptName)
	 */
	public void createConceptName(ConceptName conceptName) throws DAOException {
		if (conceptName.getCreator() == null)
			conceptName.setCreator(Context.getAuthenticatedUser());
		if (conceptName.getDateCreated() == null)
			conceptName.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().save(conceptName);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptNameService#updateConceptName(org.openmrs.ConceptName)
	 */
	public void updateConceptName(ConceptName conceptName) throws DAOException {
		if (conceptName.getCreator() == null)
			conceptName.setCreator(Context.getAuthenticatedUser());
		if (conceptName.getDateCreated() == null)
			conceptName.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().update(conceptName);
	}

	/**
	 * @see org.openmrs.api.db.ConceptSetService#createConceptSet(org.openmrs.ConceptSet)
	 */
	public void createConceptSet(ConceptSet conceptSet) throws DAOException {
		if (conceptSet.getCreator() == null)
			conceptSet.setCreator(Context.getAuthenticatedUser());
		if (conceptSet.getDateCreated() == null)
			conceptSet.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().save(conceptSet);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptSetService#updateConceptSet(org.openmrs.ConceptSet)
	 */
	public void updateConceptSet(ConceptSet conceptSet) throws DAOException {
		if (conceptSet.getCreator() == null)
			conceptSet.setCreator(Context.getAuthenticatedUser());
		if (conceptSet.getDateCreated() == null)
			conceptSet.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().update(conceptSet);
	}

	/**
	 * @see org.openmrs.api.db.ConceptSourceService#createConceptSource(org.openmrs.ConceptSource)
	 */
	public void createConceptSource(ConceptSource conceptSource) throws DAOException {
		if (conceptSource.getCreator() == null)
			conceptSource.setCreator(Context.getAuthenticatedUser());
		if (conceptSource.getDateCreated() == null)
			conceptSource.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().save(conceptSource);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptSourceService#updateConceptSource(org.openmrs.ConceptSource)
	 */
	public void updateConceptSource(ConceptSource conceptSource) throws DAOException {
		if (conceptSource.getCreator() == null)
			conceptSource.setCreator(Context.getAuthenticatedUser());
		if (conceptSource.getDateCreated() == null)
			conceptSource.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().update(conceptSource);
	}

	/**
	 * @see org.openmrs.api.db.ConceptSynonymService#createConceptSynonym(org.openmrs.ConceptSynonym)
	 */
	public void createConceptSynonym(ConceptSynonym conceptSynonym) throws DAOException {
		if (conceptSynonym.getCreator() == null)
			conceptSynonym.setCreator(Context.getAuthenticatedUser());
		if (conceptSynonym.getDateCreated() == null)
			conceptSynonym.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().save(conceptSynonym);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptSynonymService#updateConceptSynonym(org.openmrs.ConceptSynonym)
	 */
	public void updateConceptSynonym(ConceptSynonym conceptSynonym) throws DAOException {
		if (conceptSynonym.getCreator() == null)
			conceptSynonym.setCreator(Context.getAuthenticatedUser());
		if (conceptSynonym.getDateCreated() == null)
			conceptSynonym.setDateCreated(new Date());
		
		sessionFactory.getCurrentSession().update(conceptSynonym);
	}

	/**
	 * @see org.openmrs.api.db.ConceptWordService#createConceptWord(org.openmrs.ConceptWord)
	 */
	public void createConceptWord(ConceptWord conceptWord) throws DAOException {
		
		sessionFactory.getCurrentSession().save(conceptWord);
	}
	
	/**
	 * @see org.openmrs.api.db.ConceptWordService#updateConceptWord(org.openmrs.ConceptWord)
	 */
	public void updateConceptWord(ConceptWord conceptWord) throws DAOException {
		
		sessionFactory.getCurrentSession().update(conceptWord);
	}

    /**
     * @see org.openmrs.api.db.ConceptDAO#getConceptGuids()
     */
    public Map<Integer, String> getConceptGuids() {
        Map<Integer, String> ret = new HashMap<Integer, String>();
        Query q = sessionFactory.getCurrentSession().createQuery("select conceptId, guid from Concept");
        List<Object[]> list = q.list();
        for (Object[] o : list)
            ret.put((Integer) o[0], (String) o[1]);
        return ret;
    }
}
