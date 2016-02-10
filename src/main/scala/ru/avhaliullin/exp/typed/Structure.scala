package ru.avhaliullin.exp.typed

/**
  * @author avhaliullin
  */
case class Structure(name: String, fields: IndexedSeq[Structure.Field]){
  val fieldsMap = fields.map(f => f.name -> f).toMap
}

object Structure {

  case class Field(name: String, tpe: Tpe)

}