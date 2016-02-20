package duncecap

import duncecap.attr._

import scala.collection.immutable.TreeSet

abstract class EHNode(val rels: List[OptimizerRel], val selections:Array[Selection]) {
  val attrSet = rels.foldLeft(TreeSet[String]())(
    (accum: TreeSet[String], rel: OptimizerRel) => accum | TreeSet[String](rel.attrs.values: _*))
  var attrToRels:Map[Attr,List[OptimizerRel]] = PlanUtil.createAttrToRelsMapping(attrSet, rels)
  var attrToSelection:Map[Attr,List[QueryPlanSelection]] = attrSet.map(attr => (attr, PlanUtil.getSelection(attr, attrToRels))).toMap
  var outputRelation:OptimizerRel = null
  var attributeOrdering: List[Attr] = null
  var children: List[GHDNode] = List()
  var scalars = List[OptimizerRel]()

  def setAttributeOrdering(ordering: List[Attr] )

  protected def getSelection(attr:Attr): List[QueryPlanSelection] = {
    attrToSelection.getOrElse(attr, List())
  }

  def getAccessor(attr:Attr): List[QueryPlanAccessor] = {
    PlanUtil.getAccessor(attr, attrToRels, attributeOrdering)
  }

  def getSelectedAttrs(): Iterable[Attr] = {
    attrToSelection.filter({case (attr, selects) => !selects.isEmpty}).keys
  }

  def getOrderedAttrsWithAccessor(): List[Attr] = {
    PlanUtil.getOrderedAttrsWithAccessor(attributeOrdering, attrToRels)
  }

  private def getNextAnnotatedForLastMaterialized(attr:Attr, joinAggregates:Map[String,Aggregation]): Option[Attr] = {
    if (!outputRelation.attrs.values.isEmpty && outputRelation.attrs.values.last == attr) {
      val selectedAttrs = attributeOrdering.takeWhile(a => attrToSelection.contains(a) && !attrToSelection(a).isEmpty)
      if (!selectedAttrs.isEmpty) {
        Some(selectedAttrs.last)
      } else {
        attributeOrdering.dropWhile(a => a != attr).tail.find(a => joinAggregates.contains(a) && attrSet.contains(a))
      }
    } else {
      None
    }
  }

  protected def getNPRRInfo(joinAggregates:Map[String, Aggregation]) = {
    val attrsWithAccessor = getOrderedAttrsWithAccessor()
    val firstAttr = attrsWithAccessor.head
    val prevAndNextAttrMaterialized = PlanUtil.getPrevAndNextAttrNames(
      attrsWithAccessor,
      ((attr:Attr) => outputRelation.attrs.values.contains(attr)))
    val prevAndNextAttrAggregated = PlanUtil.getPrevAndNextAttrNames(
      attrsWithAccessor,
      ((attr:Attr) => joinAggregates.get(attr).isDefined && !outputRelation.attrs.values.contains(attr)))

    attrsWithAccessor.zip(prevAndNextAttrMaterialized.zip(prevAndNextAttrAggregated)).flatMap(attrAndPrevNextInfo => {
      val (attr, prevNextInfo) = attrAndPrevNextInfo
      val (materializedInfo, aggregatedInfo) = prevNextInfo
      val accessor = getAccessor(attr)
      if (accessor.isEmpty) {
        assert(false)
        None // this should not happen
      } else {
        Some(new QueryPlanAttrInfo(
          attr,
          accessor,
          outputRelation.attrs.values.contains(attr),
          getSelection(attr),
          getNextAnnotatedForLastMaterialized(attr, joinAggregates),
          PlanUtil.getAggregation(joinAggregates, attr, aggregatedInfo),
          materializedInfo._1,
          materializedInfo._2))
      }
    })
  }
}
