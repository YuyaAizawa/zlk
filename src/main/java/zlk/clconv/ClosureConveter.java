package zlk.clconv;

import static zlk.util.ErrorUtils.neverHappen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import zlk.clcalc.CcCaseBranch;
import zlk.clcalc.CcCtor;
import zlk.clcalc.CcExp;
import zlk.clcalc.CcExp.CcApp;
import zlk.clcalc.CcExp.CcCase;
import zlk.clcalc.CcExp.CcCnst;
import zlk.clcalc.CcExp.CcIf;
import zlk.clcalc.CcExp.CcLet;
import zlk.clcalc.CcExp.CcLetRec;
import zlk.clcalc.CcExp.CcMkCls;
import zlk.clcalc.CcExp.CcRecBinding;
import zlk.clcalc.CcExp.CcVar;
import zlk.clcalc.CcFunDecl;
import zlk.clcalc.CcModule;
import zlk.clcalc.CcTypeDecl;
import zlk.common.ConstValue;
import zlk.common.Type;
import zlk.common.id.Id;
import zlk.common.id.IdList;
import zlk.common.id.IdMap;
import zlk.idcalc.IcCaseBranch;
import zlk.idcalc.IcCtor;
import zlk.idcalc.IcExp;
import zlk.idcalc.IcExp.IcApp;
import zlk.idcalc.IcExp.IcCase;
import zlk.idcalc.IcExp.IcCnst;
import zlk.idcalc.IcExp.IcIf;
import zlk.idcalc.IcExp.IcLamb;
import zlk.idcalc.IcExp.IcLet;
import zlk.idcalc.IcExp.IcVarCtor;
import zlk.idcalc.IcExp.IcVarForeign;
import zlk.idcalc.IcExp.IcVarLocal;
import zlk.idcalc.IcModule;
import zlk.idcalc.IcPattern;
import zlk.idcalc.IcPattern.Var;
import zlk.idcalc.IcTypeDecl;
import zlk.idcalc.IcValDecl;
import zlk.recon.ConstraintExtractor;
import zlk.util.Location;

public final class ClosureConveter {

        private final IcModule src;
        private final IdMap<Type> type; // 変換後のIdの型を追加
        private final Set<Id> knowns;
        private final IdMap<IdList> letDependers;

        private final List<CcFunDecl> toplevels;
        private final AtomicInteger closureCount;

        public ClosureConveter(IcModule src, IdMap<Type> type, IdList builtins, IdMap<IdList> letDependers) {
                this.src = src;
                this.type = type;
                this.knowns = new HashSet<>();
                src.decls().forEach(decl -> knowns.add(decl.id()));
                src.types().forEach(union -> union.ctors().forEach(ctor -> knowns.add(ctor.id())));
                knowns.addAll(builtins);
                this.letDependers = letDependers;
                this.toplevels = new ArrayList<>();
                this.closureCount = new AtomicInteger();
        }

        public CcModule convert() {
                src.decls()
                                .stream()
                                .map(decl -> compileFunc(decl.id(), decl.args(), decl.body()))
                                .forEach(maybeCls -> maybeCls.ifPresent(cls -> {
                                        throw new RuntimeException("toplevel must not be closure: " + cls.clsFunc());
                                }));

                List<CcTypeDecl> types = src.types().stream().map(ty -> convert(ty)).toList();
                return new CcModule(src.name(), types, toplevels, src.origin());
        }

        /**
         * 関数をトップレベルに変換し追加する．クロージャに変換された場合，その作成を返す．
         * @return クロージャの生成（クロージャが必要だったとき）
         */
        private Optional<CcMkCls> compileFunc(Id id, List<IcPattern> args_, IcExp body) {
                return compileFunc(id, args_, body, true);
        }

        private Optional<CcMkCls> compileLetFunc(Id id, List<IcPattern> args_, IcExp body) {
                return compileFunc(id, args_, body, false);
        }

        private Optional<CcMkCls> compileFunc(Id id, List<IcPattern> args_, IcExp body, boolean treatSelfAsKnown) {

                knowns.add(id);
                CcExp ccBody = compile(body);
                IdList frees = fvFunc(ccBody, args_, treatSelfAsKnown ? Optional.empty() : Optional.of(id));

                if (frees.isEmpty()) {
                        toplevels.add(new CcFunDecl(id, args_, ccBody, body.loc()));
                        return Optional.empty();

                } else {
                        CcFunDecl closureFunc = makeClosure(id, frees, args_, ccBody, type.get(id).apply(args_.size()), body.loc());
                        toplevels.add(closureFunc);
                        knowns.add(closureFunc.id());
                        return Optional.of(new CcMkCls(closureFunc.id(), frees, closureFunc.loc()));
                }
        }

        /**
         * 自由変数を引数に入れた新しい関数宣言を作る
         * @param original 元の関数のId
         * @param frees 自由変数
         * @param args_ 元の関数の引数
         * @param body 元の関数の中身
         * @param retTy 元の関数の戻り値型
         * @param loc 元の関数のLocation
         * @return
         */
        private CcFunDecl makeClosure(Id original, IdList frees, List<IcPattern> args_, CcExp body, Type retTy, Location loc) {
                List<Type> types = new ArrayList<>();
                frees.forEach(id -> types.add(type.get(id)));
                args_.forEach(pat -> types.add(type.get(pat.headId())));
                types.add(retTy);

                Type clsTy = types.get(0).toTree(types.subList(1, types.size()));
                Id clsId = freshId(original);
                type.put(clsId, clsTy);

                IdMap<Id> idMap =
                                frees.stream().collect(IdMap.collector(
                                                Function.identity(),
                                                id -> Id.fromParentAndSimpleName(clsId, id.simpleName())));
                List<IcPattern> clsArgs = new ArrayList<>(args_);
                for (Id free : frees) {
                        Id newId = idMap.get(free);
                        clsArgs.add(new Var(newId, Location.noLocation()));
                        type.put(newId, type.get(free));
                }

                CcExp clsBody = body.substId(idMap);

                return new CcFunDecl(clsId, clsArgs, clsBody, loc);
        }

        private CcExp compile(IcExp exp) {
                return switch (exp) {
                case IcCnst(ConstValue value, Location loc) -> {
                        yield new CcCnst(value, loc);
                }
                case IcVarLocal(Id id, Location loc) -> {
                        yield new CcVar(id, loc);
                }
                case IcVarForeign(Id id, Type _, Location loc) -> {
                        yield new CcVar(id, loc);
                }
                case IcVarCtor(Id id, Type _, Location loc) -> {
                        yield new CcVar(id, loc);
                }
                case IcLamb(List<IcPattern> _, IcExp body, Location _) -> {
                        yield neverHappen("no anonymous abs in this version.", body.loc());
                }
                case IcApp(IcExp fun, List<IcExp> args, Location loc) -> {
                        CcExp funExp = compile(fun);
                        List<CcExp> argExps = args.stream()
                                        .map(arg -> compile(arg))
                                        .toList();
                        yield new CcApp(funExp, argExps, loc);
                }
                case IcIf(IcExp cond, IcExp thenExp, IcExp elseExp, Location loc) -> {
                        yield new CcIf(
                                        compile(cond),
                                        compile(thenExp),
                                        compile(elseExp),
                                        loc);
                }
                case IcLet(List<IcValDecl> decls, IcExp body, Location loc) -> {
                        yield compileLet(decls, body, loc);
                }
                case IcCase(IcExp target, List<IcCaseBranch> branches, Location loc) -> {
                        CcExp compiledTarget = compile(target);
                        List<CcCaseBranch> compiledBranches =
                                        branches.stream()
                                                        .map(branch -> new CcCaseBranch(
                                                                        branch.pattern(),
                                                                        compile(branch.body()),
                                                                        branch.loc()))
                                                        .toList();
                        yield new CcCase(compiledTarget, compiledBranches, loc);
                }
                };
        }

        private CcExp compileLet(List<IcValDecl> decls, IcExp body, Location loc) {
                if (decls.isEmpty()) {
                        return compile(body);
                }

                Map<Id, IcValDecl> declMap = new HashMap<>();
                IdList targets = new IdList();
                decls.forEach(decl -> {
                        declMap.put(decl.id(), decl);
                        targets.add(decl.id());
                });

                Set<Id> targetSet = new HashSet<>(targets);
                IdMap<IdList> graph = new IdMap<>();
                for (Id id : targets) {
                        IdList dependers = letDependers.getOrDefault(id, new IdList());
                        IdList filtered = dependers.stream()
                                        .filter(targetSet::contains)
                                        .collect(IdList.collector());
                        graph.put(id, filtered);
                }

                List<IdList> sccTopo = ConstraintExtractor.sccTopo(graph);
                CcExp result = compile(body);
                for (int i = sccTopo.size() - 1; i >= 0; i--) {
                        IdList scc = sccTopo.get(i);
                        SccResult sccResult = compileScc(scc, declMap, graph);
                        List<LetBinding> bindings = sccResult.bindings();
                        for (int j = bindings.size() - 1; j >= 0; j--) {
                                LetBinding binding = bindings.get(j);
                                result = new CcLet(binding.id(), binding.exp(), result, binding.loc());
                        }
                        if (!sccResult.recBindings().isEmpty()) {
                                result = new CcLetRec(sccResult.recBindings(), result, loc);
                        }
                }

                return result;
        }

        private SccResult compileScc(IdList scc, Map<Id, IcValDecl> declMap, IdMap<IdList> graph) {
                boolean recursive = isRecursive(scc, graph);
                List<LetBinding> bindings = new ArrayList<>();
                List<CcRecBinding> recBindings = new ArrayList<>();
                for (Id id : scc) {
                        IcValDecl decl = declMap.get(id);
                        if (decl.args().isEmpty()) {
                                bindings.add(new LetBinding(id, compile(decl.body()), decl.loc()));
                                continue;
                        }

                        Optional<CcMkCls> maybeCls = compileLetFunc(id, decl.args(), decl.body());
                        if (maybeCls.isEmpty()) {
                                continue;
                        }
                        if (recursive) {
                                recBindings.add(new CcRecBinding(id, maybeCls.get()));
                        } else {
                                bindings.add(new LetBinding(id, maybeCls.get(), decl.loc()));
                        }
                }
                return new SccResult(bindings, recBindings);
        }

        private boolean isRecursive(IdList scc, IdMap<IdList> graph) {
                if (scc.size() > 1) {
                        return true;
                }
                Id id = scc.get(0);
                return graph.get(id).contains(id);
        }

        private record LetBinding(Id id, CcExp exp, Location loc) {}

        private record SccResult(List<LetBinding> bindings, List<CcRecBinding> recBindings) {}

        private IdList fvFunc(CcExp body, List<IcPattern> args, Optional<Id> treatSelfAsFree) {
                IdList free = new IdList();
                Set<Id> bounded = new HashSet<>(knowns);
                treatSelfAsFree.ifPresent(bounded::remove);
                args.forEach(arg -> arg.accumulateVars(bounded));
                fv(body, bounded, free);
                return free;
        }

        private void fv(CcExp exp, Set<Id> bounded, IdList free) {
                switch (exp) {
                case CcCnst _ -> {
                }
                case CcVar(Id id, Location _) -> {
                        if (!bounded.contains(id) && !free.contains(id)) {
                                free.add(id);
                        }
                }
                case CcApp(CcExp fun, List<CcExp> args, Location _) -> {
                        fv(fun, bounded, free);
                        args.forEach(arg -> fv(arg, bounded, free));
                }
                case CcMkCls(Id clsFunc, IdList caps, Location _) -> {
                        if (!bounded.contains(clsFunc)) {
                                throw new AssertionError();
                        }
                        caps.stream().filter(id -> !bounded.contains(id) && !free.contains(id))
                                        .forEach(free::add);
                }
                case CcIf(CcExp cond, CcExp thenExp, CcExp elseExp, Location _) -> {
                        fv(cond, bounded, free);
                        fv(thenExp, bounded, free);
                        fv(elseExp, bounded, free);
                }
                case CcLet(Id varName, CcExp boundExp, CcExp body, Location _) -> {
                        bounded.add(varName);
                        fv(boundExp, bounded, free);
                        fv(body, bounded, free);
                }
                case CcLetRec(List<CcRecBinding> bindings, CcExp body, Location _) -> {
                        bindings.forEach(binding -> bounded.add(binding.id()));
                        bindings.forEach(binding -> fv(binding.mkCls(), bounded, free));
                        fv(body, bounded, free);
                }
                case CcCase(CcExp target, List<CcCaseBranch> branches, Location _) -> {
                        fv(target, bounded, free);
                        for (CcCaseBranch branch : branches) {
                                branch.pattern().accumulateVars(bounded);
                                fv(branch.body(), bounded, free);
                        }
                }
                }
        }

        private Id freshId(Id original) {
                String orgStr = original.canonicalName();

                int idx = orgStr.indexOf('.');
                if (idx == -1) {
                        throw new Error(orgStr);
                }
                String exceptModule = orgStr.substring(idx + 1);

                return Id.fromCanonicalName(
                                src.name()
                                                + Id.SEPARATOR + closureCount.getAndIncrement()
                                                + Id.SEPARATOR + exceptModule);
        }

        private CcTypeDecl convert(IcTypeDecl icType) {
                return new CcTypeDecl(icType.id(), icType.ctors().stream().map(ctor -> convert(ctor)).toList(), icType.loc());
        }

        private CcCtor convert(IcCtor icCtor) {
                return new CcCtor(icCtor.id(), icCtor.args(), icCtor.loc());
        }
}
