package zlk;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

import zlk.tester.ModuleTester;
import zlk.tester.ModuleTester.CompileLevel;

public class ConstrainerTest {
	@Test
	void selfRecursiveFunction() {
		String src ="""
		fact n =
		  if isZero n then
		    1
		  else
		    let
		      one = 1
		      nn = sub n one
		    in
		      mul n (fact nn)
		""";

		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.fact: [0],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[1], [2]]
				          header: {
				            Main.fact.n: [1],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[3]]
				                  cons: [
				                    Exists:
				                      vars: [[4], [5], [6]]
				                      cons: [
				                        Foreign: Basic.isZero:I32 -> Bool = [4],
				                        [4] = [5] -> [6],
				                        Local: Main.fact.n = [5],
				                        [6] = Bool,
				                      ],
				                    I32 = [3],
				                    Let:
				                      rigids: []
				                      flexes: []
				                      header: {
				                        Main.fact.nn: [15],
				                        Main.fact.one: [14],
				                      }
				                      headerCons: [
				                        Phase:
				                          cons: [
				                            Let:
				                              rigids: []
				                              flexes: [[16]]
				                              header: {}
				                              headerCons: [
				                                Phase:
				                                  cons: [
				                                    I32 = [16],
				                                  ]
				                                  genTargets: [],
				                              ]
				                              bodyCons:[
				                                [16] = [14],
				                              ],
				                          ]
				                          genTargets: [Main.fact.one],
				                        Phase:
				                          cons: [
				                            Let:
				                              rigids: []
				                              flexes: [[17]]
				                              header: {}
				                              headerCons: [
				                                Phase:
				                                  cons: [
				                                    Exists:
				                                      vars: [[18], [19], [20], [21]]
				                                      cons: [
				                                        Foreign: Basic.sub:I32 -> I32 -> I32 = [18],
				                                        [18] = [19] -> [20] -> [21],
				                                        Local: Main.fact.n = [19],
				                                        Local: Main.fact.one = [20],
				                                        [21] = [17],
				                                      ],
				                                  ]
				                                  genTargets: [],
				                              ]
				                              bodyCons:[
				                                [17] = [15],
				                              ],
				                          ]
				                          genTargets: [Main.fact.nn],
				                      ]
				                      bodyCons:[
				                        Exists:
				                          vars: [[7], [8], [9], [13]]
				                          cons: [
				                            Foreign: Basic.mul:I32 -> I32 -> I32 = [7],
				                            [7] = [8] -> [9] -> [13],
				                            Local: Main.fact.n = [8],
				                            Exists:
				                              vars: [[10], [11], [12]]
				                              cons: [
				                                Local: Main.fact = [10],
				                                [10] = [11] -> [12],
				                                Local: Main.fact.nn = [11],
				                                [12] = [9],
				                              ],
				                            [13] = [3],
				                          ],
				                      ],
				                    [3] = [2],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [1] -> [2] = [0],
				          ],
				      ]
				      genTargets: [Main.fact],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}

	@Test
	void mutualRecursiveFunction() {
		String src ="""
		isEven n =
		  if isZero n then
		    True
		  else
		    isOdd (sub n 1)

		isOdd n =
		  if isZero n then
		    False
		  else
		    isEven (sub n 1)
		""";

		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.isEven: [0],
				    Main.isOdd: [1],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[15], [16]]
				          header: {
				            Main.isOdd.n: [15],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[17]]
				                  cons: [
				                    Exists:
				                      vars: [[18], [19], [20]]
				                      cons: [
				                        Foreign: Basic.isZero:I32 -> Bool = [18],
				                        [18] = [19] -> [20],
				                        Local: Main.isOdd.n = [19],
				                        [20] = Bool,
				                      ],
				                    Foreign: Basic.False:Bool = [17],
				                    Exists:
				                      vars: [[21], [22], [27]]
				                      cons: [
				                        Local: Main.isEven = [21],
				                        [21] = [22] -> [27],
				                        Exists:
				                          vars: [[23], [24], [25], [26]]
				                          cons: [
				                            Foreign: Basic.sub:I32 -> I32 -> I32 = [23],
				                            [23] = [24] -> [25] -> [26],
				                            Local: Main.isOdd.n = [24],
				                            I32 = [25],
				                            [26] = [22],
				                          ],
				                        [27] = [17],
				                      ],
				                    [17] = [16],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [15] -> [16] = [1],
				          ],
				        Let:
				          rigids: []
				          flexes: [[2], [3]]
				          header: {
				            Main.isEven.n: [2],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[4]]
				                  cons: [
				                    Exists:
				                      vars: [[5], [6], [7]]
				                      cons: [
				                        Foreign: Basic.isZero:I32 -> Bool = [5],
				                        [5] = [6] -> [7],
				                        Local: Main.isEven.n = [6],
				                        [7] = Bool,
				                      ],
				                    Foreign: Basic.True:Bool = [4],
				                    Exists:
				                      vars: [[8], [9], [14]]
				                      cons: [
				                        Local: Main.isOdd = [8],
				                        [8] = [9] -> [14],
				                        Exists:
				                          vars: [[10], [11], [12], [13]]
				                          cons: [
				                            Foreign: Basic.sub:I32 -> I32 -> I32 = [10],
				                            [10] = [11] -> [12] -> [13],
				                            Local: Main.isEven.n = [11],
				                            I32 = [12],
				                            [13] = [9],
				                          ],
				                        [14] = [4],
				                      ],
				                    [4] = [3],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [2] -> [3] = [0],
				          ],
				      ]
				      genTargets: [Main.isOdd, Main.isEven],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}

	@Test
	void genericTypeInLetExp() {
		String src ="""
				type IntList =
				| Nil
				| Cons I32 IntList

				car list =
				  case list of
				  | Nil ->
				    0
				  | Cons hd tl ->
				    hd

				rectest =
				  let
				    id x =
				      x
				    res =
				      Cons (id 1) (Cons (car (id (Cons 2 Nil))) Nil)
				  in
				    res
				""";

		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.car: [0],
				    Main.rectest: [1],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[2], [3]]
				          header: {
				            Main.car.list: [2],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[4], [5]]
				                  cons: [
				                    Local: Main.car.list = [4],
				                    Let:
				                      rigids: []
				                      flexes: []
				                      header: {}
				                      headerCons: [
				                        Phase:
				                          cons: [
				                            Main.IntList = [4],
				                            I32 = [5],
				                          ]
				                          genTargets: [],
				                      ]
				                      bodyCons:[
				                        [5] = [5],
				                      ],
				                    Let:
				                      rigids: []
				                      flexes: []
				                      header: {
				                        Main.car._1.hd: I32,
				                        Main.car._1.tl: Main.IntList,
				                      }
				                      headerCons: [
				                        Phase:
				                          cons: [
				                            Main.IntList = [4],
				                            Local: Main.car._1.hd = [5],
				                          ]
				                          genTargets: [],
				                      ]
				                      bodyCons:[
				                        [5] = [5],
				                      ],
				                    [5] = [3],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [2] -> [3] = [0],
				          ],
				      ]
				      genTargets: [Main.car],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[6]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Let:
				                  rigids: []
				                  flexes: []
				                  header: {
				                    Main.rectest.id: [7],
				                    Main.rectest.res: [8],
				                  }
				                  headerCons: [
				                    Phase:
				                      cons: [
				                        Let:
				                          rigids: []
				                          flexes: [[9], [10]]
				                          header: {
				                            Main.rectest.id.x: [9],
				                          }
				                          headerCons: [
				                            Phase:
				                              cons: [
				                                Local: Main.rectest.id.x = [10],
				                              ]
				                              genTargets: [],
				                          ]
				                          bodyCons:[
				                            [9] -> [10] = [7],
				                          ],
				                      ]
				                      genTargets: [Main.rectest.id],
				                    Phase:
				                      cons: [
				                        Let:
				                          rigids: []
				                          flexes: [[11]]
				                          header: {}
				                          headerCons: [
				                            Phase:
				                              cons: [
				                                Exists:
				                                  vars: [[12], [13], [17], [32]]
				                                  cons: [
				                                    Foreign: Main.Cons:I32 -> Main.IntList -> Main.IntList = [12],
				                                    [12] = [13] -> [17] -> [32],
				                                    Exists:
				                                      vars: [[14], [15], [16]]
				                                      cons: [
				                                        Local: Main.rectest.id = [14],
				                                        [14] = [15] -> [16],
				                                        I32 = [15],
				                                        [16] = [13],
				                                      ],
				                                    Exists:
				                                      vars: [[18], [19], [30], [31]]
				                                      cons: [
				                                        Foreign: Main.Cons:I32 -> Main.IntList -> Main.IntList = [18],
				                                        [18] = [19] -> [30] -> [31],
				                                        Exists:
				                                          vars: [[20], [21], [29]]
				                                          cons: [
				                                            Local: Main.car = [20],
				                                            [20] = [21] -> [29],
				                                            Exists:
				                                              vars: [[22], [23], [28]]
				                                              cons: [
				                                                Local: Main.rectest.id = [22],
				                                                [22] = [23] -> [28],
				                                                Exists:
				                                                  vars: [[24], [25], [26], [27]]
				                                                  cons: [
				                                                    Foreign: Main.Cons:I32 -> Main.IntList -> Main.IntList = [24],
				                                                    [24] = [25] -> [26] -> [27],
				                                                    I32 = [25],
				                                                    Foreign: Main.Nil:Main.IntList = [26],
				                                                    [27] = [23],
				                                                  ],
				                                                [28] = [21],
				                                              ],
				                                            [29] = [19],
				                                          ],
				                                        Foreign: Main.Nil:Main.IntList = [30],
				                                        [31] = [17],
				                                      ],
				                                    [32] = [11],
				                                  ],
				                              ]
				                              genTargets: [],
				                          ]
				                          bodyCons:[
				                            [11] = [8],
				                          ],
				                      ]
				                      genTargets: [Main.rectest.res],
				                  ]
				                  bodyCons:[
				                    Local: Main.rectest.res = [6],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [6] = [1],
				          ],
				      ]
				      genTargets: [Main.rectest],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}

	@Test
	void leakOuterStruct() {
		String src ="""
				pair a b s = s a b
				fst p = p fst_
				fst_ x y = x
				snd p = p snd_
				snd_ x y = y

				id x = x

				p = pair id id

				u = fst p
				v = snd p

				r1 = u 1
				r2 = v True
				""";
		var module = new ModuleTester(src, CompileLevel.TYPE_CINT);
		String expected =
				"""
				Let:
				  rigids: []
				  flexes: []
				  header: {
				    Main.fst: [1],
				    Main.fst_: [2],
				    Main.id: [5],
				    Main.p: [6],
				    Main.pair: [0],
				    Main.r1: [9],
				    Main.r2: [10],
				    Main.snd: [3],
				    Main.snd_: [4],
				    Main.u: [7],
				    Main.v: [8],
				  }
				  headerCons: [
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[11], [12], [13], [14]]
				          header: {
				            Main.pair.a: [11],
				            Main.pair.b: [12],
				            Main.pair.s: [13],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[15], [16], [17], [18]]
				                  cons: [
				                    Local: Main.pair.s = [15],
				                    [15] = [16] -> [17] -> [18],
				                    Local: Main.pair.a = [16],
				                    Local: Main.pair.b = [17],
				                    [18] = [14],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [11] -> [12] -> [13] -> [14] = [0],
				          ],
				      ]
				      genTargets: [Main.pair],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[32], [33], [34]]
				          header: {
				            Main.snd_.x: [32],
				            Main.snd_.y: [33],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Local: Main.snd_.y = [34],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [32] -> [33] -> [34] = [4],
				          ],
				      ]
				      genTargets: [Main.snd_],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[27], [28]]
				          header: {
				            Main.snd.p: [27],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[29], [30], [31]]
				                  cons: [
				                    Local: Main.snd.p = [29],
				                    [29] = [30] -> [31],
				                    Local: Main.snd_ = [30],
				                    [31] = [28],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [27] -> [28] = [3],
				          ],
				      ]
				      genTargets: [Main.snd],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[35], [36]]
				          header: {
				            Main.id.x: [35],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Local: Main.id.x = [36],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [35] -> [36] = [5],
				          ],
				      ]
				      genTargets: [Main.id],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[37]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[38], [39], [40], [41]]
				                  cons: [
				                    Local: Main.pair = [38],
				                    [38] = [39] -> [40] -> [41],
				                    Local: Main.id = [39],
				                    Local: Main.id = [40],
				                    [41] = [37],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [37] = [6],
				          ],
				      ]
				      genTargets: [Main.p],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[46]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[47], [48], [49]]
				                  cons: [
				                    Local: Main.snd = [47],
				                    [47] = [48] -> [49],
				                    Local: Main.p = [48],
				                    [49] = [46],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [46] = [8],
				          ],
				      ]
				      genTargets: [Main.v],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[54]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[55], [56], [57]]
				                  cons: [
				                    Local: Main.v = [55],
				                    [55] = [56] -> [57],
				                    Foreign: Basic.True:Bool = [56],
				                    [57] = [54],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [54] = [10],
				          ],
				      ]
				      genTargets: [Main.r2],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[24], [25], [26]]
				          header: {
				            Main.fst_.x: [24],
				            Main.fst_.y: [25],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Local: Main.fst_.x = [26],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [24] -> [25] -> [26] = [2],
				          ],
				      ]
				      genTargets: [Main.fst_],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[19], [20]]
				          header: {
				            Main.fst.p: [19],
				          }
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[21], [22], [23]]
				                  cons: [
				                    Local: Main.fst.p = [21],
				                    [21] = [22] -> [23],
				                    Local: Main.fst_ = [22],
				                    [23] = [20],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [19] -> [20] = [1],
				          ],
				      ]
				      genTargets: [Main.fst],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[42]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[43], [44], [45]]
				                  cons: [
				                    Local: Main.fst = [43],
				                    [43] = [44] -> [45],
				                    Local: Main.p = [44],
				                    [45] = [42],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [42] = [7],
				          ],
				      ]
				      genTargets: [Main.u],
				    Phase:
				      cons: [
				        Let:
				          rigids: []
				          flexes: [[50]]
				          header: {}
				          headerCons: [
				            Phase:
				              cons: [
				                Exists:
				                  vars: [[51], [52], [53]]
				                  cons: [
				                    Local: Main.u = [51],
				                    [51] = [52] -> [53],
				                    I32 = [52],
				                    [53] = [50],
				                  ],
				              ]
				              genTargets: [],
				          ]
				          bodyCons:[
				            [50] = [9],
				          ],
				      ]
				      genTargets: [Main.r1],
				  ]
				  bodyCons:[
				    Exists:
				      vars: []
				      cons: [],
				  ]""";
		assertEquals(expected, module.getConstraint().buildString().replace("\r", ""));
	}
}
